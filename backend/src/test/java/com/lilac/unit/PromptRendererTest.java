package com.lilac.unit;

import com.lilac.service.PromptRenderer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 提示词渲染器单测（步骤 D10 配套）。
 *
 * <p>核心点：原代码用链式 {@code .replace()} 手工拼，漏变量会静默留下
 * 字面量 {@code {xxx}}，模型会把它当正文处理。
 */
@DisplayName("提示词模板渲染")
class PromptRendererTest {

    private final PromptRenderer renderer = new PromptRenderer();

    @Test
    @DisplayName("单变量替换")
    void singleVar() {
        assertEquals("选题：AI 写作",
                renderer.render("选题：{topic}", Map.of("topic", "AI 写作")));
    }

    @Test
    @DisplayName("多变量替换")
    void multipleVars() {
        String tpl = "主标题：{mainTitle}\n副标题：{subTitle}";
        String out = renderer.render(tpl, Map.of("mainTitle", "标题A", "subTitle", "副标题B"));
        assertEquals("主标题：标题A\n副标题：副标题B", out);
    }

    @Test
    @DisplayName("同一变量出现多次都会被替换")
    void repeatedVar() {
        assertEquals("A-A-A", renderer.render("{x}-{x}-{x}", Map.of("x", "A")));
    }

    @Test
    @DisplayName("缺失变量保留字面量，便于日志发现")
    void missingVarKept() {
        String out = renderer.render("A={a}, B={b}", Map.of("a", "1"));
        assertEquals("A=1, B={b}", out,
                "缺失变量应保留 {b} 字面量，而不是替换成空串 —— 否则模型输出会悄悄跑偏");
    }

    @Test
    @DisplayName("变量值含 $ 和 \\ 不会被当成正则替换符")
    void valueWithSpecialChars() {
        String out = renderer.render("价格：{price}", Map.of("price", "$100 \\ 元"));
        assertEquals("价格：$100 \\ 元", out);
    }

    @Test
    @DisplayName("变量值本身含 {xxx} 不会被二次替换")
    void noRecursiveReplace() {
        String out = renderer.render("{a}", Map.of("a", "{b}", "b", "SHOULD_NOT_APPEAR"));
        assertEquals("{b}", out, "替换应只做一轮，避免变量值里的占位符被再次展开");
    }

    @Test
    @DisplayName("null 与空模板安全返回")
    void nullAndEmpty() {
        assertEquals("", renderer.render(null, Map.of()));
        assertEquals("", renderer.render("", Map.of()));
    }

    @Test
    @DisplayName("null 变量表不会 NPE")
    void nullVars() {
        assertEquals("原样 {a}", renderer.render("原样 {a}", null));
    }

    @Test
    @DisplayName("双花括号占位符不被当作变量（IMAGE 模板用到）")
    void doubleBraceIgnored() {
        // AGENT4 模板里大量使用 {{IMAGE_PLACEHOLDER_N}}，不能被单花括号规则吃掉
        String out = renderer.render("正文{{IMAGE_PLACEHOLDER_1}}结尾", Map.of());
        assertTrue(out.contains("{{IMAGE_PLACEHOLDER_1}}"),
                "双花括号是给模型看的字面占位符，不应被渲染器处理");
    }

    @Test
    @DisplayName("提取变量名（保持顺序、去重）")
    void extractVars() {
        Set<String> vars = renderer.extractVariables("A={a} B={b} C={a}");
        assertEquals(java.util.List.of("a", "b"), java.util.List.copyOf(vars));
    }

    @Test
    @DisplayName("无变量模板提取为空集")
    void extractNone() {
        assertTrue(renderer.extractVariables("纯文本，没有占位符").isEmpty());
        assertTrue(renderer.extractVariables(null).isEmpty());
    }

    @Test
    @DisplayName("真实模板：AGENT1 渲染后不含未替换占位符")
    void realAgent1Template() {
        String tpl = "选题：{topic}\n请返回 JSON";
        String out = renderer.render(tpl, Map.of("topic", "Spring Boot 入门"));
        assertFalse(out.contains("{topic}"));
        assertTrue(out.contains("Spring Boot 入门"));
    }

    @Test
    @DisplayName("extraVars 提供多余变量不影响结果")
    void extraVarsIgnored() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("a", "1");
        vars.put("unused", "x");
        assertEquals("1", renderer.render("{a}", vars));
    }
}
