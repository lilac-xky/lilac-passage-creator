package com.lilac.service;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.lilac.annotation.AgentExecution;
import com.lilac.constant.PromptConstant;
import com.lilac.domain.dto.article.ArticleState;
import com.lilac.domain.dto.image.ImageRequest;
import com.lilac.enums.ArticleStyleEnum;
import com.lilac.enums.ImageMethodEnum;
import com.lilac.enums.SseMessageTypeEnum;
import com.lilac.strategy.ImageServiceStrategy;
import com.lilac.utils.GsonUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 文章生成服务
 */
@Service
@Slf4j
public class ArticleAgentService {

    @Resource
    private DashScopeChatModel chatModel;
    @Resource
    private CosService cosService;
    @Resource
    private ImageServiceStrategy imageServiceStrategy;

    /**
     * 阶段1：生成标题方案（3-5个）
     *
     * @param state         文章状态
     * @param streamHandler 流式输出处理器
     */
    public void executePhase1_GenerateTitles(ArticleState state, Consumer<String> streamHandler) {
        try {
            // 智能体1：生成标题方案
            log.info("阶段1：开始生成标题方案, taskId={}", state.getTaskId());
            getProxy().agent1GenerateTitleOptions(state);
            streamHandler.accept(SseMessageTypeEnum.AGENT1_COMPLETE.getValue());
            log.info("阶段1：标题方案生成完成, taskId={}, optionsCount={}", state.getTaskId(), state.getTitleOptions().size());
        } catch (Exception e) {
            log.error("阶段1：标题方案生成失败, taskId={}", state.getTaskId(), e);
            throw new RuntimeException("标题方案生成失败: " + e.getMessage(), e);
        }
    }

    /**
     * 阶段2：生成大纲（用户选择标题后）
     *
     * @param state         文章状态
     * @param streamHandler 流式输出处理器
     */
    public void executePhase2_GenerateOutline(ArticleState state, Consumer<String> streamHandler) {
        try {
            // 智能体2：生成大纲（流式输出）
            log.info("阶段2：开始生成大纲, taskId={}", state.getTaskId());
            getProxy().agent2GenerateOutline(state, streamHandler);
            streamHandler.accept(SseMessageTypeEnum.AGENT2_COMPLETE.getValue());
            log.info("阶段2：大纲生成完成, taskId={}", state.getTaskId());
        } catch (Exception e) {
            log.error("阶段2：大纲生成失败, taskId={}", state.getTaskId(), e);
            throw new RuntimeException("大纲生成失败: " + e.getMessage(), e);
        }
    }

    /**
     * 阶段3：生成正文+配图（用户确认大纲后）
     *
     * @param state         文章状态
     * @param streamHandler 流式输出处理器
     */
    public void executePhase3_GenerateContent(ArticleState state, Consumer<String> streamHandler) {
        try {
            // 获取代理对象
            ArticleAgentService proxy = getProxy();

            // 智能体3：生成正文（流式输出）
            log.info("阶段3：开始生成正文, taskId={}", state.getTaskId());
            proxy.agent3GenerateContent(state, streamHandler);
            streamHandler.accept(SseMessageTypeEnum.AGENT3_COMPLETE.getValue());

            // 智能体4：分析配图需求
            log.info("阶段3：开始分析配图需求, taskId={}", state.getTaskId());
            proxy.agent4AnalyzeImageRequirements(state);
            streamHandler.accept(SseMessageTypeEnum.AGENT4_COMPLETE.getValue());

            // 智能体5：生成配图
            log.info("阶段3：开始生成配图, taskId={}", state.getTaskId());
            proxy.agent5GenerateImages(state, streamHandler);
            streamHandler.accept(SseMessageTypeEnum.AGENT5_COMPLETE.getValue());

            // 图文合成：将配图插入正文
            log.info("阶段3：开始图文合成, taskId={}", state.getTaskId());
            proxy.mergeImagesIntoContent(state);
            streamHandler.accept(SseMessageTypeEnum.MERGE_COMPLETE.getValue());

            log.info("阶段3：正文生成完成, taskId={}", state.getTaskId());
        } catch (Exception e) {
            log.error("阶段3：正文生成失败, taskId={}", state.getTaskId(), e);
            throw new RuntimeException("正文生成失败: " + e.getMessage(), e);
        }
    }

    /**
     * 智能体1：生成标题方案（3-5个）
     */
    @AgentExecution(value = "agent1_generate_titles", description = "生成标题方案")
    public void agent1GenerateTitleOptions(ArticleState state) {
        String prompt = PromptConstant.AGENT1_TITLE_PROMPT.replace("{topic}", state.getTopic()) + getStylePrompt(state.getStyle());
        String content = callLlm(prompt);
        List<ArticleState.TitleOption> titleOptions = parseJsonListResponse(
                content,
                new TypeToken<List<ArticleState.TitleOption>>(){},
                "标题方案"
        );
        state.setTitleOptions(titleOptions);
        log.info("智能体1：标题方案生成成功, optionsCount={}", titleOptions.size());
    }

    /**
     * 智能体2：生成大纲（流式输出）
     */
    @AgentExecution(value = "agent2_generate_outline", description = "生成文章大纲")
    public void agent2GenerateOutline(ArticleState state, Consumer<String> streamHandler) {
        // 构建 prompt，根据是否有用户补充描述插入对应部分
        String descriptionSection = "";
        if (state.getUserDescription() != null && !state.getUserDescription().trim().isEmpty()) {
            descriptionSection = PromptConstant.AGENT2_DESCRIPTION_SECTION.replace("{userDescription}", state.getUserDescription());
        }

        String prompt = PromptConstant.AGENT2_OUTLINE_PROMPT
                .replace("{mainTitle}", state.getTitle().getMainTitle())
                .replace("{subTitle}", state.getTitle().getSubTitle())
                .replace("{descriptionSection}", descriptionSection)
                + getStylePrompt(state.getStyle());

        String content = callLlmWithStreaming(prompt, streamHandler, SseMessageTypeEnum.AGENT2_STREAMING);
        ArticleState.OutlineResult outlineResult = parseJsonResponse(content, ArticleState.OutlineResult.class, "大纲");
        state.setOutline(outlineResult);
        log.info("智能体2：大纲生成成功, sections={}", outlineResult.getSections().size());
    }

    /**
     * 智能体3：生成正文（流式输出）
     */
    @AgentExecution(value = "agent3_generate_content", description = "生成文章正文")
    public void agent3GenerateContent(ArticleState state, Consumer<String> streamHandler) {
        String outlineText = GsonUtils.toJson(state.getOutline().getSections());
        String prompt = PromptConstant.AGENT3_CONTENT_PROMPT
                .replace("{mainTitle}", state.getTitle().getMainTitle())
                .replace("{subTitle}", state.getTitle().getSubTitle())
                .replace("{outline}", outlineText)
                + getStylePrompt(state.getStyle());
        String content = callLlmWithStreaming(prompt, streamHandler, SseMessageTypeEnum.AGENT3_STREAMING);
        state.setContent(content);
        log.info("智能体3：正文生成成功, length={}", content.length());
    }

    /**
     * 智能体4：分析配图需求（在正文中插入占位符）
     */
    @AgentExecution(value = "agent4_analyze_image_requirements", description = "分析配图需求")
    public void agent4AnalyzeImageRequirements(ArticleState state) {
        // 构建可用配图方式说明
        String availableMethods = buildAvailableMethodsDescription(state.getEnabledImageMethods());
        String prompt = PromptConstant.AGENT4_IMAGE_REQUIREMENTS_PROMPT
                .replace("{mainTitle}", state.getTitle().getMainTitle())
                .replace("{content}", state.getContent())
                .replace("{availableMethods}", availableMethods);
        String content = callLlm(prompt);
        ArticleState.Agent4Result agent4Result = parseJsonResponse(content, ArticleState.Agent4Result.class, "配图需求");

        enforceEnabledImageMethods(agent4Result, state.getEnabledImageMethods());

        // 更新正文为包含占位符的版本
        state.setContent(agent4Result.getContentWithPlaceholders());
        state.setImageRequirements(agent4Result.getImageRequirements());
        log.info("智能体4：配图需求分析成功, count={}, 已在正文中插入占位符", agent4Result.getImageRequirements().size());
    }

    /**
     * LLM 输出不可信，实际执行前必须再次应用用户选择的图片方式白名单。
     */
    private void enforceEnabledImageMethods(ArticleState.Agent4Result result, List<String> enabledImageMethods) {
        if (result.getImageRequirements() == null) {
            return;
        }
        // 获取允许的图片方式
        Set<String> allowedMethods = Arrays.stream(ImageMethodEnum.values())
                .filter(method -> method != null && !method.isFallback())
                .filter(method -> enabledImageMethods == null || enabledImageMethods.isEmpty()
                        || enabledImageMethods.contains(method.getValue()))
                .filter(this::isImageMethodAvailable)
                .map(ImageMethodEnum::getValue)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        // 筛选出允许的图片需求
        List<ArticleState.ImageRequirement> acceptedRequirements = new ArrayList<>();
        String content = result.getContentWithPlaceholders();
        // 遍历图片需求
        for (ArticleState.ImageRequirement requirement : result.getImageRequirements()) {
            if (!allowedMethods.contains(requirement.getImageSource())) {
                log.info("跳过未启用或未配置的配图方式, position={}, imageSource={}", requirement.getPosition(), requirement.getImageSource());
                if (content != null && requirement.getPlaceholderId() != null) {
                    content = content.replace(requirement.getPlaceholderId(), "");
                }
                continue;
            }
            acceptedRequirements.add(requirement);
        }
        result.setContentWithPlaceholders(content);
        result.setImageRequirements(acceptedRequirements);
    }

    /**
     * 判断图片方式可用性
     */
    private boolean isImageMethodAvailable(ImageMethodEnum method) {
        var service = imageServiceStrategy.getService(method);
        return service != null && service.isAvailable();
    }

    /**
     * 智能体5：生成配图（串行执行）
     */
    @AgentExecution(value = "agent5_generate_images", description = "生成配图")
    public void agent5GenerateImages(ArticleState state, Consumer<String> streamHandler) {
        List<ArticleState.ImageResult> imageResults = new ArrayList<>();

        for (ArticleState.ImageRequirement requirement : state.getImageRequirements()) {
            String imageSource = requirement.getImageSource();
            log.info("智能体5：开始获取配图, position={}, imageSource={}, keywords={}",
                    requirement.getPosition(), imageSource, requirement.getKeywords());

            // 构建图片请求对象
            ImageRequest imageRequest = ImageRequest.builder()
                    .keywords(requirement.getKeywords())
                    .prompt(requirement.getPrompt())
                    .position(requirement.getPosition())
                    .type(requirement.getType())
                    .build();

            // 使用策略模式获取图片并统一上传到 COS
            ImageServiceStrategy.ImageResult result = imageServiceStrategy.getImageAndUpload(imageSource, imageRequest);
            String cosUrl = result.getUrl();
            ImageMethodEnum method = result.getMethod();

            // 创建配图结果（URL 已经是 COS 地址）
            ArticleState.ImageResult imageResult = buildImageResult(requirement, cosUrl, method);
            imageResults.add(imageResult);

            // 推送单张配图完成
            String imageCompleteMessage = SseMessageTypeEnum.IMAGE_COMPLETE.getStreamingPrefix() + GsonUtils.toJson(imageResult);
            streamHandler.accept(imageCompleteMessage);
            log.info("智能体5：配图获取并上传成功, position={}, method={}, cosUrl={}", requirement.getPosition(), method.getValue(), cosUrl);
        }
        state.setImages(imageResults);
        log.info("智能体5：所有配图生成并上传完成, count={}", imageResults.size());
    }

    /**
     * AI 修改大纲
     *
     * @param mainTitle        主标题
     * @param subTitle         副标题
     * @param currentOutline   当前大纲
     * @param modifySuggestion 用户修改建议
     * @return 修改后的大纲
     */
    @AgentExecution(value = "ai_modify_outline", description = "AI修改大纲")
    public List<ArticleState.OutlineSection> aiModifyOutline(String mainTitle, String subTitle,
                                                             List<ArticleState.OutlineSection> currentOutline, String modifySuggestion) {
        String currentOutlineJson = GsonUtils.toJson(currentOutline);
        String prompt = PromptConstant.AI_MODIFY_OUTLINE_PROMPT
                .replace("{mainTitle}", mainTitle)
                .replace("{subTitle}", subTitle)
                .replace("{currentOutline}", currentOutlineJson)
                .replace("{modifySuggestion}", modifySuggestion);

        String content = callLlm(prompt);
        ArticleState.OutlineResult outlineResult = parseJsonResponse(content, ArticleState.OutlineResult.class, "修改后的大纲");

        log.info("AI修改大纲成功, sectionsCount={}", outlineResult.getSections().size());
        return outlineResult.getSections();
    }

    /**
     * 图文合成：根据占位符将配图插入正文
     */
    @AgentExecution(value = "agent6_merge_content", description = "图文合成")
    public void mergeImagesIntoContent(ArticleState state) {
        String content = state.getContent();
        List<ArticleState.ImageResult> images = state.getImages();
        if (images == null || images.isEmpty()) {
            state.setFullContent(content);
            return;
        }
        String fullContent = content;

        // 遍历所有配图，根据占位符替换为实际图片
        for (ArticleState.ImageResult image : images) {
            String placeholder = image.getPlaceholderId();
            if (placeholder != null && !placeholder.isEmpty()) {
                String imageMarkdown = "![" + image.getDescription() + "](" + image.getUrl() + ")";
                fullContent = fullContent.replace(placeholder, imageMarkdown);
            }
        }
        state.setFullContent(fullContent);
        log.info("图文合成完成, fullContentLength={}", fullContent.length());
    }

    /**
     * 调用 LLM（非流式）
     */
    private String callLlm(String prompt) {
        ChatResponse response = chatModel.call(new Prompt(new UserMessage(prompt)));
        return response.getResult().getOutput().getText();
    }

    /**
     * 调用 LLM（流式输出）
     */
    private String callLlmWithStreaming(String prompt, Consumer<String> streamHandler, SseMessageTypeEnum messageType) {
        StringBuilder contentBuilder = new StringBuilder();
        Flux<ChatResponse> streamResponse = chatModel.stream(new Prompt(new UserMessage(prompt)));

        streamResponse
                .doOnNext(response -> {
                    String chunk = response.getResult().getOutput().getText();
                    if (chunk != null && !chunk.isEmpty()) {
                        contentBuilder.append(chunk);
                        streamHandler.accept(messageType.getStreamingPrefix() + chunk);
                    }
                })
                .doOnError(error -> log.error("LLM 流式调用失败, messageType={}", messageType, error))
                .blockLast();
        return contentBuilder.toString();
    }

    /**
     * 构建可用配图方式说明
     */
    private String buildAvailableMethodsDescription(List<String> enabledImageMethods) {
        return Arrays.stream(ImageMethodEnum.values())
                .filter(method -> !method.isFallback())
                .filter(method -> enabledImageMethods == null || enabledImageMethods.isEmpty()
                        || enabledImageMethods.contains(method.getValue()))
                .filter(this::isImageMethodAvailable)
                .map(method -> method.getValue() + ": " + method.getDescription())
                .collect(Collectors.joining("\n"));
    }

    /**
     * 解析 JSON 响应
     */
    private <T> T parseJsonResponse(String content, Class<T> clazz, String name) {
        try {
            return GsonUtils.fromJson(content, clazz);
        } catch (JsonSyntaxException e) {
            log.error("{}解析失败, content={}", name, content, e);
            throw new RuntimeException(name + "解析失败");
        }
    }

    /**
     * 解析 JSON 列表响应
     */
    private <T> T parseJsonListResponse(String content, TypeToken<T> typeToken, String name) {
        try {
            return GsonUtils.fromJson(content, typeToken);
        } catch (JsonSyntaxException e) {
            log.error("{}解析失败, content={}", name, content, e);
            throw new RuntimeException(name + "解析失败");
        }
    }

    /**
     * 构建配图结果对象
     */
    private ArticleState.ImageResult buildImageResult(ArticleState.ImageRequirement requirement, String imageUrl, ImageMethodEnum method) {
        ArticleState.ImageResult imageResult = new ArticleState.ImageResult();
        imageResult.setPosition(requirement.getPosition());
        imageResult.setUrl(imageUrl);
        imageResult.setMethod(method.getValue());
        imageResult.setKeywords(requirement.getKeywords());
        imageResult.setSectionTitle(requirement.getSectionTitle());
        imageResult.setDescription(requirement.getType());
        imageResult.setPlaceholderId(requirement.getPlaceholderId());  // 记录占位符ID
        return imageResult;
    }

    /**
     * 在章节标题后插入对应图片
     */
    private void insertImageAfterSection(StringBuilder fullContent, List<ArticleState.ImageResult> images, String sectionTitle) {
        for (ArticleState.ImageResult image : images) {
            if (image.getPosition() > 1 && image.getSectionTitle() != null && sectionTitle.contains(image.getSectionTitle().trim())) {
                fullContent.append("\n![")
                        .append(image.getDescription()).append("](")
                        .append(image.getUrl()).append(")\n");
                break;
            }
        }
    }

    /**
     * 根据风格获取对应的 Prompt 附加内容
     */
    private String getStylePrompt(String style) {
        if (style == null || style.isEmpty()) {
            return "";
        }
        ArticleStyleEnum styleEnum = ArticleStyleEnum.getEnumByValue(style);
        if (styleEnum == null) {
            return "";
        }
        return switch (styleEnum) {
            case TECH -> PromptConstant.STYLE_TECH_PROMPT;
            case EMOTIONAL -> PromptConstant.STYLE_EMOTIONAL_PROMPT;
            case EDUCATIONAL -> PromptConstant.STYLE_EDUCATIONAL_PROMPT;
            case HUMOROUS -> PromptConstant.STYLE_HUMOROUS_PROMPT;
        };
    }

    /**
     * 获取当前类的代理对象
     * 用于解决 Spring AOP 同类方法调用代理失效问题
     */
    private ArticleAgentService getProxy() {
        try {
            return (ArticleAgentService) AopContext.currentProxy();
        } catch (IllegalStateException e) {
            // 如果获取代理失败，返回 this（降级处理）
            log.warn("获取 AOP 代理对象失败，使用原始对象: {}", e.getMessage());
            return this;
        }
    }
}
