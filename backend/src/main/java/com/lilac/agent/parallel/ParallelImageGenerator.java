package com.lilac.agent.parallel;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.lilac.agent.context.StreamHandlerContext;
import com.lilac.agent.tools.ImageGenerationTool;
import com.lilac.domain.dto.article.ArticleState;
import com.lilac.enums.SseMessageTypeEnum;
import com.lilac.utils.GsonUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 并行图片生成器
 * 根据 imageSource 分组，并行执行不同类型的图片生成任务
 */
@Component
@Slf4j
public class ParallelImageGenerator implements NodeAction {

    @Resource
    private ImageGenerationTool imageGenerationTool;

    /**
     * 配图生成专用线程池（见 {@code AsyncConfig#imageExecutor}）。
     *
     * <p>改造前使用无 executor 的 {@code CompletableFuture.runAsync(...)}，
     * 任务会落到全局 {@code ForkJoinPool.commonPool()}：
     * 该池是给 CPU 密集型任务设计的（并行度 = 核数 - 1），
     * 却被用来执行 HTTP + COS 这类阻塞 IO，既低估并行度，又会污染全局池。
     */
    @Resource
    @Qualifier("imageExecutor")
    private Executor imageExecutor;

    /**
     * 单个图源分组的总超时（秒）。
     * 一个分组内部是多张图串行，所以要给足时间。
     */
    @Value("${article.image.group-timeout-seconds:180}")
    private long groupTimeoutSeconds;

    public static final String INPUT_IMAGE_REQUIREMENTS = "imageRequirements";
    public static final String OUTPUT_IMAGES = "images";

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        @SuppressWarnings("unchecked")
        List<ArticleState.ImageRequirement> imageRequirements = state.value(INPUT_IMAGE_REQUIREMENTS)
                .map(v -> {
                    if (v instanceof List) {
                        List<?> list = (List<?>) v;
                        if (list.isEmpty()) {
                            return new ArrayList<ArticleState.ImageRequirement>();
                        }
                        if (list.get(0) instanceof ArticleState.ImageRequirement) {
                            return (List<ArticleState.ImageRequirement>) v;
                        }
                        return convertToImageRequirements(list);
                    }
                    return new ArrayList<ArticleState.ImageRequirement>();
                })
                .orElse(new ArrayList<>());
        
        // 从 ThreadLocal 获取流式处理器
        Consumer<String> streamHandler = StreamHandlerContext.get();
        log.info("ParallelImageGenerator 开始执行: 配图需求数量={}", imageRequirements.size());
        
        if (imageRequirements.isEmpty()) {
            log.info("没有配图需求，跳过图片生成");
            return Map.of(OUTPUT_IMAGES, new ArrayList<>());
        }
        
        // 按 imageSource 分组
        Map<String, List<ArticleState.ImageRequirement>> groupedBySource = imageRequirements.stream()
                .collect(Collectors.groupingBy(ArticleState.ImageRequirement::getImageSource));
        
        log.info("配图需求按类型分组: {}", 
                groupedBySource.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().size())));
        
        // 并行执行不同类型的图片生成
        List<ArticleState.ImageResult> allImages = executeParallel(groupedBySource, streamHandler);
        
        // 按 position 排序
        allImages.sort((a, b) -> {
            Integer posA = a.getPosition() != null ? a.getPosition() : 0;
            Integer posB = b.getPosition() != null ? b.getPosition() : 0;
            return posA.compareTo(posB);
        });
        
        log.info("ParallelImageGenerator 执行完成: 成功生成 {} 张图片", allImages.size());
        return Map.of(OUTPUT_IMAGES, allImages);
    }

    /**
     * 并行执行图片生成任务
     * 不同 imageSource 类型并行执行，同一类型内部串行执行
     *
     * <p><b>改造点（步骤 C3）</b>
     * <ol>
     *   <li>使用专用 {@code imageExecutor}，不再落到 ForkJoinPool.commonPool</li>
     *   <li>每个分组加独立超时，避免单个图源卡死拖垮整个阶段</li>
     *   <li>单个分组失败/超时只影响自己，其余分组照常完成（失败隔离）</li>
     * </ol>
     */
    private List<ArticleState.ImageResult> executeParallel(
            Map<String, List<ArticleState.ImageRequirement>> groupedBySource,
            Consumer<String> streamHandler) {

        // 使用线程安全的列表收集结果
        CopyOnWriteArrayList<ArticleState.ImageResult> allImages = new CopyOnWriteArrayList<>();

        // 为每种 imageSource 创建异步任务（显式指定专用线程池）
        List<CompletableFuture<Void>> futures = groupedBySource.entrySet().stream()
                .map(entry -> {
                    String imageSource = entry.getKey();
                    List<ArticleState.ImageRequirement> requirements = entry.getValue();

                    CompletableFuture<Void> future = CompletableFuture.runAsync(
                            () -> processOneSource(imageSource, requirements, streamHandler, allImages),
                            imageExecutor);

                    // 单个分组超时：orTimeout 会在超时后让该 future 异常完成，
                    // 由下面的 allOf 统一处理，不影响其它分组
                    return future.orTimeout(groupTimeoutSeconds, TimeUnit.SECONDS);
                })
                .toList();

        // 等待所有任务完成。
        // 注意：不能直接用 allOf(...).join()，否则任一分组超时/异常会抛出来，
        // 导致已经成功的图片结果一起丢掉。这里逐个 handle，只记日志。
        CompletableFuture.allOf(futures.stream()
                        .map(f -> f.handle((r, ex) -> {
                            if (ex != null) {
                                Throwable cause = (ex instanceof CompletionException && ex.getCause() != null)
                                        ? ex.getCause() : ex;
                                if (cause instanceof TimeoutException) {
                                    log.warn("配图分组超时（{}秒），已跳过，其余分组不受影响", groupTimeoutSeconds);
                                } else {
                                    log.error("配图分组执行失败，已跳过，其余分组不受影响", cause);
                                }
                            }
                            return null;
                        }))
                        .toArray(CompletableFuture[]::new))
                .join();

        return new ArrayList<>(allImages);
    }

    /**
     * 处理单个图源分组：组内串行，且逐张隔离异常
     */
    private void processOneSource(String imageSource,
                                  List<ArticleState.ImageRequirement> requirements,
                                  Consumer<String> streamHandler,
                                  CopyOnWriteArrayList<ArticleState.ImageResult> allImages) {

        log.info("开始处理 {} 类型的图片，数量: {}", imageSource, requirements.size());

        // 同一类型内部串行执行
        for (ArticleState.ImageRequirement req : requirements) {
            try {
                ImageGenerationTool.ImageGenerationResult result =
                        imageGenerationTool.generateImageDirect(
                                req.getImageSource(),
                                req.getKeywords(),
                                req.getPrompt(),
                                req.getPosition(),
                                req.getType(),
                                req.getSectionTitle(),
                                req.getPlaceholderId()
                        );

                if (result.isSuccess()) {
                    ArticleState.ImageResult imageResult = convertToImageResult(result);
                    allImages.add(imageResult);

                    // 推送单张配图完成消息
                    if (streamHandler != null) {
                        String message = SseMessageTypeEnum.IMAGE_COMPLETE.getStreamingPrefix() + GsonUtils.toJson(imageResult);
                        streamHandler.accept(message);
                    }
                    log.info("图片生成成功: imageSource={}, position={}", imageSource, req.getPosition());
                } else {
                    log.warn("图片生成失败: imageSource={}, position={}, error={}",
                            imageSource, req.getPosition(), result.getError());
                }
            } catch (Exception e) {
                // 单张失败不影响同组其它图片
                log.error("图片生成异常: imageSource={}, position={}", imageSource, req.getPosition(), e);
            }
        }
        log.info("完成处理 {} 类型的图片", imageSource);
    }

    /**
     * 转换 ImageGenerationResult 为 ArticleState.ImageResult
     */
    private ArticleState.ImageResult convertToImageResult(ImageGenerationTool.ImageGenerationResult genResult) {
        ArticleState.ImageResult imageResult = new ArticleState.ImageResult();
        imageResult.setPosition(genResult.getPosition());
        imageResult.setUrl(genResult.getUrl());
        imageResult.setMethod(genResult.getMethod());
        imageResult.setKeywords(genResult.getKeywords());
        imageResult.setSectionTitle(genResult.getSectionTitle());
        imageResult.setDescription(genResult.getDescription());
        imageResult.setPlaceholderId(genResult.getPlaceholderId());
        return imageResult;
    }

    /**
     * 转换列表为 ImageRequirement 列表
     */
    private List<ArticleState.ImageRequirement> convertToImageRequirements(List<?> list) {
        List<ArticleState.ImageRequirement> results = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof ArticleState.ImageRequirement) {
                results.add((ArticleState.ImageRequirement) item);
            } else if (item instanceof Map) {
                String json = GsonUtils.toJson(item);
                ArticleState.ImageRequirement req = GsonUtils.fromJson(json, ArticleState.ImageRequirement.class);
                results.add(req);
            }
        }
        return results;
    }
}
