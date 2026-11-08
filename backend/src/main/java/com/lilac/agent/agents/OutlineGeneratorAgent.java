package com.lilac.agent.agents;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.lilac.agent.context.StreamHandlerContext;
import com.lilac.agent.context.TokenUsageContext;
import com.lilac.constant.PromptConstant;
import com.lilac.domain.dto.article.ArticleState;
import com.lilac.enums.SseMessageTypeEnum;
import com.lilac.service.PromptTemplateService;
import com.lilac.service.StylePromptService;
import com.lilac.utils.GsonUtils;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.function.Consumer;

/**
 * 大纲生成 Agent
 * 根据标题生成文章大纲（支持流式输出）
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OutlineGeneratorAgent implements NodeAction {

    private final DashScopeChatModel chatModel;

    @Resource
    private PromptTemplateService promptTemplateService;

    @Resource
    private StylePromptService stylePromptService;

    public static final String INPUT_MAIN_TITLE = "mainTitle";
    public static final String INPUT_SUB_TITLE = "subTitle";
    public static final String INPUT_USER_DESCRIPTION = "userDescription";
    public static final String INPUT_STYLE = "style";
    public static final String OUTPUT_OUTLINE = "outline";

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String mainTitle = state.value(INPUT_MAIN_TITLE)
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("缺少主标题参数"));
        
        String subTitle = state.value(INPUT_SUB_TITLE)
                .map(Object::toString)
                .orElse("");
        
        String userDescription = state.value(INPUT_USER_DESCRIPTION)
                .map(Object::toString)
                .orElse(null);
        
        String style = state.value(INPUT_STYLE)
                .map(Object::toString)
                .orElse(null);
        
        log.info("OutlineGeneratorAgent 开始执行: mainTitle={}, subTitle={}", mainTitle, subTitle);
        
        // 构建用户描述部分（模板从 DB 读取，DB 不可用时回退 PromptConstant）
        String descriptionSection = "";
        if (userDescription != null && !userDescription.trim().isEmpty()) {
            descriptionSection = promptTemplateService.render(
                    "AGENT2_DESCRIPTION_SECTION",
                    PromptConstant.AGENT2_DESCRIPTION_SECTION,
                    Map.of("userDescription", userDescription));
        }

        // 构建 prompt（模板从 DB 读取）
        String prompt = promptTemplateService.render(
                "AGENT2_OUTLINE_PROMPT",
                PromptConstant.AGENT2_OUTLINE_PROMPT,
                Map.of(
                        "mainTitle", mainTitle,
                        "subTitle", subTitle == null ? "" : subTitle,
                        "descriptionSection", descriptionSection))
                + stylePromptService.getStylePrompt(style);
        
        // 获取流式处理器
        Consumer<String> streamHandler = StreamHandlerContext.get();
        
        // 调用 LLM（流式输出）
        String content = callLlmWithStreaming(prompt, streamHandler);
        
        // 解析结果（容错：模型可能返回代码围栏或夹杂解释文字）
        ArticleState.OutlineResult outlineResult = GsonUtils.fromLlmOutput(content, ArticleState.OutlineResult.class);
        log.info("OutlineGeneratorAgent 执行完成: 生成了 {} 个章节", outlineResult.getSections().size());
        return Map.of(OUTPUT_OUTLINE, outlineResult);
    }

    /**
     * 调用 LLM（流式输出）
     */
    private String callLlmWithStreaming(String prompt, Consumer<String> streamHandler) {
        StringBuilder contentBuilder = new StringBuilder();
        Flux<ChatResponse> streamResponse = chatModel.stream(new Prompt(new UserMessage(prompt)));
        
        streamResponse
                .doOnNext(response -> {
                    // 采集 token 用量（步骤 E1）
                    TokenUsageContext.record(response);
                    String chunk = response.getResult().getOutput().getText();
                    if (chunk != null && !chunk.isEmpty()) {
                        contentBuilder.append(chunk);
                        // 带前缀发送流式消息
                        if (streamHandler != null) {
                            streamHandler.accept(SseMessageTypeEnum.AGENT2_STREAMING.getStreamingPrefix() + chunk);
                        }
                    }
                })
                .doOnError(error -> log.error("OutlineGeneratorAgent 流式调用失败", error))
                .blockLast();
        return contentBuilder.toString();
    }
}