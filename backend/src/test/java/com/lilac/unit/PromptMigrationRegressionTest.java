package com.lilac.unit;

import com.lilac.constant.PromptConstant;
import com.lilac.service.PromptRenderer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 提示词迁移回归测试（步骤 D10 推广后）。
 *
 * <p>核心保障：从 {@link PromptConstant} 走兜底路径时，
 * 渲染结果必须与原先链式 {@code .replace()} 的产物<b>完全一致</b>。
 *
 * <p>如果这个测试失败，说明迁移改变了实际发给模型的 prompt ——
 * 这会静默影响生成质量，且不会报错。
 */
@DisplayName("提示词迁移回归")
class PromptMigrationRegressionTest {

    private final PromptRenderer renderer = new PromptRenderer();

    @Test
    @DisplayName("AGENT1 渲染结果 = 原 replace 结果")
    void agent1Equivalent() {
        String topic = "Spring Boot 入门";
        String oldWay = PromptConstant.AGENT1_TITLE_PROMPT.replace("{topic}", topic);
        String newWay = renderer.render(PromptConstant.AGENT1_TITLE_PROMPT, Map.of("topic", topic));
        assertEquals(oldWay, newWay);
    }

    @Test
    @DisplayName("AGENT2 渲染结果 = 原链式 replace 结果")
    void agent2Equivalent() {
        String mainTitle = "主标题A";
        String subTitle = "副标题B";
        String desc = "用户补充要求：写详细点";

        String oldWay = PromptConstant.AGENT2_OUTLINE_PROMPT
                .replace("{mainTitle}", mainTitle)
                .replace("{subTitle}", subTitle)
                .replace("{descriptionSection}", desc);

        String newWay = renderer.render(PromptConstant.AGENT2_OUTLINE_PROMPT, Map.of(
                "mainTitle", mainTitle,
                "subTitle", subTitle,
                "descriptionSection", desc));

        assertEquals(oldWay, newWay);
    }

    @Test
    @DisplayName("AGENT2 无补充描述时 descriptionSection 为空串")
    void agent2WithoutDescription() {
        String oldWay = PromptConstant.AGENT2_OUTLINE_PROMPT
                .replace("{mainTitle}", "T")
                .replace("{subTitle}", "S")
                .replace("{descriptionSection}", "");

        String newWay = renderer.render(PromptConstant.AGENT2_OUTLINE_PROMPT, Map.of(
                "mainTitle", "T", "subTitle", "S", "descriptionSection", ""));

        assertEquals(oldWay, newWay);
    }

    @Test
    @DisplayName("AGENT3 渲染结果 = 原 replace 结果")
    void agent3Equivalent() {
        String outline = "[{\"section\":1,\"title\":\"章节\"}]";
        String oldWay = PromptConstant.AGENT3_CONTENT_PROMPT
                .replace("{mainTitle}", "T")
                .replace("{subTitle}", "S")
                .replace("{outline}", outline);

        String newWay = renderer.render(PromptConstant.AGENT3_CONTENT_PROMPT, Map.of(
                "mainTitle", "T", "subTitle", "S", "outline", outline));

        assertEquals(oldWay, newWay);
    }

    @Test
    @DisplayName("AGENT4 渲染结果 = 原 replace 结果")
    void agent4Equivalent() {
        String oldWay = PromptConstant.AGENT4_IMAGE_REQUIREMENTS_PROMPT
                .replace("{mainTitle}", "T")
                .replace("{content}", "正文内容")
                .replace("{availableMethods}", "PEXELS: 图库");

        String newWay = renderer.render(PromptConstant.AGENT4_IMAGE_REQUIREMENTS_PROMPT, Map.of(
                "mainTitle", "T",
                "content", "正文内容",
                "availableMethods", "PEXELS: 图库"));

        assertEquals(oldWay, newWay);
    }

    @Test
    @DisplayName("AGENT4 模板不含 methodUsageGuide 占位符（原代码有多余 replace）")
    void agent4HasNoMethodUsageGuide() {
        assertFalse(PromptConstant.AGENT4_IMAGE_REQUIREMENTS_PROMPT.contains("{methodUsageGuide}"),
                "原代码 replace({methodUsageGuide}) 是无效遗留，模板里根本没有这个占位符");
    }

    @Test
    @DisplayName("AGENT4 的双花括号占位符在渲染后保持原样")
    void agent4KeepsDoubleBrace() {
        String out = renderer.render(PromptConstant.AGENT4_IMAGE_REQUIREMENTS_PROMPT, Map.of(
                "mainTitle", "T", "content", "C", "availableMethods", "M"));
        assertTrue(out.contains("{{IMAGE_PLACEHOLDER_N}}"),
                "双花括号是给模型看的字面占位符，必须原样保留");
        assertTrue(out.contains("{{ICON_PLACEHOLDER_N}}"));
    }

    @Test
    @DisplayName("SVG 模板渲染 = 原 replace 结果")
    void svgEquivalent() {
        String req = "画一个三层架构图";
        String oldWay = PromptConstant.SVG_DIAGRAM_GENERATION_PROMPT.replace("{requirement}", req);
        String newWay = renderer.render(PromptConstant.SVG_DIAGRAM_GENERATION_PROMPT, Map.of("requirement", req));
        assertEquals(oldWay, newWay);
    }

    @Test
    @DisplayName("AI_MODIFY_OUTLINE 渲染 = 原链式 replace 结果")
    void modifyOutlineEquivalent() {
        String oldWay = PromptConstant.AI_MODIFY_OUTLINE_PROMPT
                .replace("{mainTitle}", "T")
                .replace("{subTitle}", "S")
                .replace("{currentOutline}", "[{\"section\":1}]")
                .replace("{modifySuggestion}", "多加一节");

        String newWay = renderer.render(PromptConstant.AI_MODIFY_OUTLINE_PROMPT, Map.of(
                "mainTitle", "T",
                "subTitle", "S",
                "currentOutline", "[{\"section\":1}]",
                "modifySuggestion", "多加一节"));

        assertEquals(oldWay, newWay);
    }

    @Test
    @DisplayName("风格模板无占位符，渲染后原样返回")
    void styleTemplatesUnchanged() {
        String[] styles = {
                PromptConstant.STYLE_TECH_PROMPT,
                PromptConstant.STYLE_EMOTIONAL_PROMPT,
                PromptConstant.STYLE_EDUCATIONAL_PROMPT,
                PromptConstant.STYLE_HUMOROUS_PROMPT
        };
        for (String tpl : styles) {
            assertEquals(tpl, renderer.render(tpl, Map.of()));
            assertTrue(renderer.extractVariables(tpl).isEmpty(), "风格模板不应含占位符");
        }
    }

    @Test
    @DisplayName("所有模板渲染后都不残留自身定义的占位符")
    void noResidualPlaceholders() {
        // 给每个模板提供全部可能变量，断言渲染后无 {xxx} 残留（双花括号除外）
        // 注意 Map.of 最多 10 对，这里用 Map.ofEntries
        Map<String, String> allVars = Map.ofEntries(
                Map.entry("topic", "T"),
                Map.entry("mainTitle", "T"),
                Map.entry("subTitle", "S"),
                Map.entry("descriptionSection", "D"),
                Map.entry("userDescription", "U"),
                Map.entry("outline", "O"),
                Map.entry("content", "C"),
                Map.entry("availableMethods", "A"),
                Map.entry("methodUsageGuide", "G"),
                Map.entry("currentOutline", "CO"),
                Map.entry("modifySuggestion", "MS"),
                Map.entry("requirement", "R"));

        String[] templates = {
                PromptConstant.AGENT1_TITLE_PROMPT,
                PromptConstant.AGENT2_OUTLINE_PROMPT,
                PromptConstant.AGENT2_DESCRIPTION_SECTION,
                PromptConstant.AGENT3_CONTENT_PROMPT,
                PromptConstant.AGENT4_IMAGE_REQUIREMENTS_PROMPT,
                PromptConstant.SVG_DIAGRAM_GENERATION_PROMPT,
                PromptConstant.AI_MODIFY_OUTLINE_PROMPT
        };

        for (String tpl : templates) {
            String rendered = renderer.render(tpl, allVars);
            // 移除双花括号内容后再检查单花括号残留
            String stripped = rendered.replaceAll("\\{\\{[^}]*}}", "");
            assertFalse(stripped.matches("(?s).*\\{[a-zA-Z][a-zA-Z0-9_]*}.*"),
                    "渲染后仍有未替换的单花括号占位符: " + rendered.substring(0, Math.min(200, rendered.length())));
        }
    }
}
