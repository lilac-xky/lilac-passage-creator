package com.lilac.service;

import com.google.gson.reflect.TypeToken;
import com.lilac.agent.ArticleAgentOrchestrator;
import com.lilac.config.AgentConfig;
import com.lilac.domain.dto.article.ArticleState;
import com.lilac.domain.entity.Article;
import com.lilac.enums.ArticlePhaseEnum;
import com.lilac.enums.ArticleStatusEnum;
import com.lilac.enums.SseMessageTypeEnum;
import com.lilac.manager.SseEmitterManager;
import com.lilac.utils.GsonUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文章异步服务
 */
@Service
@Slf4j
public class ArticleAsyncService {

    @Resource
    private ArticleAgentService articleAgentService;
    @Resource
    private SseEmitterManager sseEmitterManager;
    @Resource
    private ArticleService articleService;
    @Resource
    private ArticleAgentOrchestrator articleAgentOrchestrator;
    @Resource
    private AgentConfig agentConfig;
    @Resource
    private QuotaService quotaService;

    /**
     * 任务失败时的统一收尾（步骤 C4）。
     *
     * <p>做三件事：
     * <ol>
     *   <li>把任务状态置为 FAILED 并记录错误信息</li>
     *   <li><b>退还创建任务时扣掉的配额</b>（幂等，重复调用不会多退）</li>
     *   <li>推送 SSE 错误消息并关闭连接</li>
     * </ol>
     *
     * <p>改造前三处 catch 都只做了 1 和 3，用户白扣了一次配额。
     *
     * <p>注意：退还配额失败不应影响失败状态与 SSE 推送，
     * 所以单独 try-catch，只记日志。
     */
    private void handlePhaseFailure(String taskId, String phaseName, Exception e) {
        log.error("{}异步任务失败, taskId={}", phaseName, taskId, e);

        // ① 标记失败
        try {
            articleService.updateArticleStatus(taskId, ArticleStatusEnum.FAILED, e.getMessage());
        } catch (Exception ex) {
            log.error("更新失败状态时出错, taskId={}", taskId, ex);
        }

        // ② 退还配额（幂等）
        try {
            boolean refunded = quotaService.refundQuotaOnce(taskId);
            if (refunded) {
                log.info("{}失败，已退还配额, taskId={}", phaseName, taskId);
            }
        } catch (Exception ex) {
            // 退还失败不能掩盖原始错误，也不能阻断后续 SSE 通知
            log.error("{}失败后退还配额时出错, taskId={}", phaseName, taskId, ex);
        }

        // ③ 通知前端
        try {
            sendSseMessage(taskId, SseMessageTypeEnum.ERROR, Map.of("message", e.getMessage()));
            sseEmitterManager.complete(taskId);
        } catch (Exception ex) {
            log.error("推送失败消息时出错, taskId={}", taskId, ex);
        }
    }

    /**
     * 阶段1：异步生成标题方案
     *
     * @param taskId 任务ID
     * @param topic  选题
     * @param style  文章风格（可为空）
     */
    @Async("articleExecutor")
    public void executePhase1(String taskId, String topic, String style) {
        boolean useOrchestrator = agentConfig.isOrchestratorEnabled();
        log.info("阶段1异步任务开始, taskId={}, topic={}, style={}, 使用多智能体编排={}", taskId, topic, style, useOrchestrator);

        try {
            // 更新状态和阶段
            articleService.updateArticleStatus(taskId, ArticleStatusEnum.PROCESSING, null);
            articleService.updatePhase(taskId, ArticlePhaseEnum.TITLE_GENERATING);

            // 创建状态对象
            ArticleState state = new ArticleState();
            state.setTaskId(taskId);
            state.setTopic(topic);
            state.setStyle(style);

            // 执行阶段1：生成标题方案（根据配置选择执行方式）
            if (useOrchestrator) {
                articleAgentOrchestrator.executePhase1_GenerateTitles(state, message -> {
                    handleAgentMessage(taskId, message, state);
                });
            } else {
                articleAgentService.executePhase1_GenerateTitles(state, message -> {
                    handleAgentMessage(taskId, message, state);
                });
            }

            // 保存标题方案到数据库
            articleService.saveTitleOptions(taskId, state.getTitleOptions());
            // 更新阶段为等待选择标题
            articleService.updatePhase(taskId, ArticlePhaseEnum.TITLE_SELECTING);

            // 推送标题方案生成完成消息
            Map<String, Object> data = new HashMap<>();
            data.put("titleOptions", state.getTitleOptions());
            sendSseMessage(taskId, SseMessageTypeEnum.TITLES_GENERATED, data);
            log.info("阶段1异步任务完成, taskId={}", taskId);
        } catch (Exception e) {
            handlePhaseFailure(taskId, "阶段1", e);
        }
    }

    /**
     * 阶段2：异步生成大纲（用户确认标题后调用）
     *
     * @param taskId 任务ID
     */
    @Async("articleExecutor")
    public void executePhase2(String taskId) {
        boolean useOrchestrator = agentConfig.isOrchestratorEnabled();
        log.info("阶段2异步任务开始, taskId={}, 使用多智能体编排={}", taskId, useOrchestrator);

        try {
            // 获取文章信息
            Article article = articleService.getByTaskId(taskId);
            if (article == null) {
                throw new RuntimeException("文章不存在");
            }

            // 创建状态对象
            ArticleState state = new ArticleState();
            state.setTaskId(taskId);
            state.setStyle(article.getStyle());
            state.setUserDescription(article.getUserDescription());

            // 设置标题
            ArticleState.TitleResult title = new ArticleState.TitleResult();
            title.setMainTitle(article.getMainTitle());
            title.setSubTitle(article.getSubTitle());
            state.setTitle(title);

            // 执行阶段2：生成大纲（根据配置选择执行方式）
            if (useOrchestrator) {
                articleAgentOrchestrator.executePhase2_GenerateOutline(state, message -> {
                    handleAgentMessage(taskId, message, state);
                });
            } else {
                articleAgentService.executePhase2_GenerateOutline(state, message -> {
                    handleAgentMessage(taskId, message, state);
                });
            }

            // 保存大纲到数据库
            Article articleToUpdate = articleService.getByTaskId(taskId);
            articleToUpdate.setOutline(GsonUtils.toJson(state.getOutline().getSections()));
            articleService.updateById(articleToUpdate);
            // 更新阶段为等待编辑大纲
            articleService.updatePhase(taskId, ArticlePhaseEnum.OUTLINE_EDITING);

            // 推送大纲生成完成消息
            Map<String, Object> data = new HashMap<>();
            data.put("outline", state.getOutline().getSections());
            sendSseMessage(taskId, SseMessageTypeEnum.OUTLINE_GENERATED, data);
            log.info("阶段2异步任务完成, taskId={}", taskId);
        } catch (Exception e) {
            handlePhaseFailure(taskId, "阶段2", e);
        }
    }

    /**
     * 阶段3：异步生成正文+配图（用户确认大纲后调用）
     *
     * @param taskId 任务ID
     */
    @Async("articleExecutor")
    public void executePhase3(String taskId) {
        boolean useOrchestrator = agentConfig.isOrchestratorEnabled();
        log.info("阶段3异步任务开始, taskId={}, 使用多智能体编排={}", taskId, useOrchestrator);

        try {
            // 获取文章信息
            Article article = articleService.getByTaskId(taskId);
            if (article == null) {
                throw new RuntimeException("文章不存在");
            }

            // 创建状态对象
            ArticleState state = new ArticleState();
            state.setTaskId(taskId);
            state.setStyle(article.getStyle());

            // 从数据库获取允许的配图方式
            List<String> enabledMethods = null;
            if (article.getEnabledImageMethods() != null) {
                enabledMethods = GsonUtils.fromJson(article.getEnabledImageMethods(), new TypeToken<List<String>>(){});
            }
            state.setEnabledImageMethods(enabledMethods);

            // 设置标题
            ArticleState.TitleResult title = new ArticleState.TitleResult();
            title.setMainTitle(article.getMainTitle());
            title.setSubTitle(article.getSubTitle());
            state.setTitle(title);

            // 设置大纲
            List<ArticleState.OutlineSection> outlineSections = GsonUtils.fromJson(
                    article.getOutline(),
                    new TypeToken<List<ArticleState.OutlineSection>>(){}
            );
            ArticleState.OutlineResult outlineResult = new ArticleState.OutlineResult();
            outlineResult.setSections(outlineSections);
            state.setOutline(outlineResult);

            // 执行阶段3：生成正文+配图（根据配置选择执行方式）
            if (useOrchestrator) {
                articleAgentOrchestrator.executePhase3_GenerateContent(state, message -> {
                    handleAgentMessage(taskId, message, state);
                });
            } else {
                articleAgentService.executePhase3_GenerateContent(state, message -> {
                    handleAgentMessage(taskId, message, state);
                });
            }

            // 保存完整文章到数据库
            articleService.saveArticleContent(taskId, state);
            // 更新状态为已完成
            articleService.updateArticleStatus(taskId, ArticleStatusEnum.COMPLETED, null);
            // 推送完成消息
            sendSseMessage(taskId, SseMessageTypeEnum.ALL_COMPLETE, Map.of("taskId", taskId));
            // 完成 SSE 连接
            sseEmitterManager.complete(taskId);
            log.info("阶段3异步任务完成, taskId={}", taskId);
        } catch (Exception e) {
            handlePhaseFailure(taskId, "阶段3", e);
        }
    }

    /**
     * 处理智能体消息并推送
     */
    private void handleAgentMessage(String taskId, String message, ArticleState state) {
        Map<String, Object> data = buildMessageData(message, state);
        if (data != null) {
            sseEmitterManager.send(taskId, GsonUtils.toJson(data));
        }
    }

    /**
     * 构建消息数据
     */
    private Map<String, Object> buildMessageData(String message, ArticleState state) {
        // 处理流式消息（带冒号分隔符）
        String streamingPrefix2 = SseMessageTypeEnum.AGENT2_STREAMING.getStreamingPrefix();
        String streamingPrefix3 = SseMessageTypeEnum.AGENT3_STREAMING.getStreamingPrefix();
        String imageCompletePrefix = SseMessageTypeEnum.IMAGE_COMPLETE.getStreamingPrefix();
        if (message.startsWith(streamingPrefix2)) {
            return buildStreamingData(SseMessageTypeEnum.AGENT2_STREAMING, message.substring(streamingPrefix2.length()));
        }
        if (message.startsWith(streamingPrefix3)) {
            return buildStreamingData(SseMessageTypeEnum.AGENT3_STREAMING, message.substring(streamingPrefix3.length()));
        }
        if (message.startsWith(imageCompletePrefix)) {
            String imageJson = message.substring(imageCompletePrefix.length());
            return buildImageCompleteData(imageJson);
        }
        // 处理完成消息（枚举值）
        return buildCompleteMessageData(message, state);
    }

    /**
     * 构建流式输出数据
     */
    private Map<String, Object> buildStreamingData(SseMessageTypeEnum type, String content) {
        Map<String, Object> data = new HashMap<>();
        data.put("type", type.getValue());
        data.put("content", content);
        return data;
    }

    /**
     * 构建图片完成数据
     */
    private Map<String, Object> buildImageCompleteData(String imageJson) {
        Map<String, Object> data = new HashMap<>();
        data.put("type", SseMessageTypeEnum.IMAGE_COMPLETE.getValue());
        data.put("image", GsonUtils.fromJson(imageJson, ArticleState.ImageResult.class));
        return data;
    }

    /**
     * 构建完成消息数据
     */
    private Map<String, Object> buildCompleteMessageData(String message, ArticleState state) {
        Map<String, Object> data = new HashMap<>();

        if (SseMessageTypeEnum.AGENT1_COMPLETE.getValue().equals(message)) {
            data.put("type", SseMessageTypeEnum.AGENT1_COMPLETE.getValue());
            data.put("title", state.getTitle());
        } else if (SseMessageTypeEnum.AGENT2_COMPLETE.getValue().equals(message)) {
            data.put("type", SseMessageTypeEnum.AGENT2_COMPLETE.getValue());
            data.put("outline", state.getOutline().getSections());
        } else if (SseMessageTypeEnum.AGENT3_COMPLETE.getValue().equals(message)) {
            data.put("type", SseMessageTypeEnum.AGENT3_COMPLETE.getValue());
        } else if (SseMessageTypeEnum.AGENT4_COMPLETE.getValue().equals(message)) {
            data.put("type", SseMessageTypeEnum.AGENT4_COMPLETE.getValue());
            data.put("imageRequirements", state.getImageRequirements());
        } else if (SseMessageTypeEnum.AGENT5_COMPLETE.getValue().equals(message)) {
            data.put("type", SseMessageTypeEnum.AGENT5_COMPLETE.getValue());
            data.put("images", state.getImages());
        } else if (SseMessageTypeEnum.MERGE_COMPLETE.getValue().equals(message)) {
            data.put("type", SseMessageTypeEnum.MERGE_COMPLETE.getValue());
            data.put("fullContent", state.getFullContent());
        } else {
            return null;
        }

        return data;
    }

    /**
     * 发送 SSE 消息
     */
    private void sendSseMessage(String taskId, SseMessageTypeEnum type, Map<String, Object> additionalData) {
        Map<String, Object> data = new HashMap<>();
        data.put("type", type.getValue());
        data.putAll(additionalData);
        sseEmitterManager.send(taskId, GsonUtils.toJson(data));
    }
}
