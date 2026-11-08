package com.lilac.service;

import com.lilac.constant.PromptConstant;
import com.lilac.enums.ArticleStyleEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 文章风格提示词服务（步骤 D10 推广）
 *
 * <p><b>为什么要抽这个类</b>
 *
 * <p>改造前，下面这段代码在 4 个类里各复制了一份：
 * {@code TitleGeneratorAgent} / {@code OutlineGeneratorAgent} /
 * {@code ContentGeneratorAgent} / {@code ArticleAgentService}。
 *
 * <pre>
 * private String getStylePrompt(String style) {
 *     ...
 *     return switch (styleEnum) {
 *         case TECH -> PromptConstant.STYLE_TECH_PROMPT;
 *         case EMOTIONAL -> PromptConstant.STYLE_EMOTIONAL_PROMPT;
 *         ...
 *     };
 * }
 * </pre>
 *
 * <p>复制粘贴的代价不只是重复，更重要的是：
 * <b>改一处漏三处</b>。之前把提示词迁到数据库时，
 * 就出现了「其中一个 Agent 已改、其余三个还在读常量」的不一致状态。
 *
 * <p>抽出来后，风格提示词的读取逻辑只有一份，
 * 以后调整风格 prompt 或增加新风格，改这一个地方即可。
 */
@Service
@Slf4j
public class StylePromptService {

    @Resource
    private PromptTemplateService promptTemplateService;

    /** 各风格在 prompt_template 表中的模板名 */
    private static final String TEMPLATE_TECH = "STYLE_TECH_PROMPT";
    private static final String TEMPLATE_EMOTIONAL = "STYLE_EMOTIONAL_PROMPT";
    private static final String TEMPLATE_EDUCATIONAL = "STYLE_EDUCATIONAL_PROMPT";
    private static final String TEMPLATE_HUMOROUS = "STYLE_HUMOROUS_PROMPT";

    /**
     * 根据风格值获取附加提示词。
     *
     * @param style 风格值：tech / emotional / educational / humorous，可为空
     * @return 附加提示词；风格为空或非法时返回空串（不追加任何内容）
     */
    public String getStylePrompt(String style) {
        if (style == null || style.isEmpty()) {
            return "";
        }

        ArticleStyleEnum styleEnum = ArticleStyleEnum.getEnumByValue(style);
        if (styleEnum == null) {
            // 非法风格不报错，退化为"不加风格"，与原实现行为一致
            log.debug("未知的文章风格: {}，不追加风格提示", style);
            return "";
        }

        return switch (styleEnum) {
            case TECH -> promptTemplateService.getContent(
                    TEMPLATE_TECH, PromptConstant.STYLE_TECH_PROMPT);
            case EMOTIONAL -> promptTemplateService.getContent(
                    TEMPLATE_EMOTIONAL, PromptConstant.STYLE_EMOTIONAL_PROMPT);
            case EDUCATIONAL -> promptTemplateService.getContent(
                    TEMPLATE_EDUCATIONAL, PromptConstant.STYLE_EDUCATIONAL_PROMPT);
            case HUMOROUS -> promptTemplateService.getContent(
                    TEMPLATE_HUMOROUS, PromptConstant.STYLE_HUMOROUS_PROMPT);
        };
    }
}
