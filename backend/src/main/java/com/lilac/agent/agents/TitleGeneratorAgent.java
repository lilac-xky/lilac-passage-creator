package com.lilac.agent.agents;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.google.gson.reflect.TypeToken;
import com.lilac.agent.context.TokenUsageContext;
import com.lilac.constant.PromptConstant;
import com.lilac.domain.dto.article.ArticleState;
import com.lilac.enums.ArticleStyleEnum;
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

import java.util.List;
import java.util.Map;

/**
 * 标题生成 Agent
 * 根据选题生成 3 ~ 5 个爆款标题方案
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TitleGeneratorAgent implements NodeAction {

    private final DashScopeChatModel chatModel;

    /**
     * 提示词模板服务（步骤 D10）。
     * 从 DB 读模板，DB 不可用时自动回退到 PromptConstant。
     */
    @Resource
    private PromptTemplateService promptTemplateService;

    /**
     * 风格提示词服务（步骤 D10 推广：原 4 处重复的 getStylePrompt 已抽出）
     */
    @Resource
    private StylePromptService stylePromptService;

    public static final String INPUT_TOPIC = "topic";
    public static final String INPUT_STYLE = "style";
    public static final String OUTPUT_TITLE_OPTIONS = "titleOptions";

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String topic = state.value(INPUT_TOPIC)
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("缺少选题参数"));
        
        String style = state.value(INPUT_STYLE)
                .map(Object::toString)
                .orElse(null);
        
        log.info("TitleGeneratorAgent 开始执行: topic={}, style={}", topic, style);
        
        // 构建 prompt —— 模板从数据库读取（步骤 D10），DB 不可用时回退 PromptConstant
        String titleTemplate = promptTemplateService.render(
                "AGENT1_TITLE_PROMPT",
                PromptConstant.AGENT1_TITLE_PROMPT,
                Map.of("topic", topic == null ? "" : topic));
        String prompt = titleTemplate + stylePromptService.getStylePrompt(style);
        
        // 调用 LLM
        ChatResponse response = chatModel.call(new Prompt(new UserMessage(prompt)));
        // 采集 token 用量（步骤 E1）
        TokenUsageContext.record(response);
        String content = response.getResult().getOutput().getText();
        
        // 解析结果（容错：模型可能返回代码围栏或夹杂解释文字）
        List<ArticleState.TitleOption> titleOptions =
                GsonUtils.fromLlmOutput(content, new TypeToken<List<ArticleState.TitleOption>>(){});
        log.info("TitleGeneratorAgent 执行完成: 生成了 {} 个标题方案", titleOptions.size());
        return Map.of(OUTPUT_TITLE_OPTIONS, titleOptions);
    }
}