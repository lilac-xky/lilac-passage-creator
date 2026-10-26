package com.lilac.unit;

import com.lilac.utils.GsonUtils;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JSON 工具单测。
 *
 * <p>GsonUtils 是四个 Agent 解析 LLM 输出的唯一入口。
 * 这里<b>刻意钉住它当前"不容错"的行为</b>：
 * 遇到 ```json 围栏、前后有多余文字时会直接抛异常。
 *
 * <p>这些用例对应步骤 05（LLM JSON 容错）要解决的问题，
 * 改造时它们会失败——那正是改造生效的证明。
 */
@DisplayName("GsonUtils 序列化/反序列化")
class GsonUtilsTest {

    @Test
    @DisplayName("正常 JSON 能正确反序列化")
    void parseNormalJson() {
        Map<String, Object> map = GsonUtils.fromJson("{\"a\":1}", Map.class);
        assertEquals(1.0, map.get("a"));
    }

    @Test
    @DisplayName("泛型集合能正确反序列化")
    void parseGenericList() {
        List<String> list = GsonUtils.fromJson("[\"x\",\"y\"]", new TypeToken<List<String>>() {
        });
        assertEquals(List.of("x", "y"), list);
    }

    @Test
    @DisplayName("中文不会被转义成 \\uXXXX（disableHtmlEscaping 生效）")
    void chineseNotEscaped() {
        String json = GsonUtils.toJson(Map.of("t", "中文"));
        assertTrue(json.contains("中文"), "开启 HTML 转义会导致入库后中文变成乱码字符");
    }

    @Test
    @DisplayName("null 字段序列化后端仍可解析")
    void nullSerializesSafely() {
        String json = GsonUtils.toJson(Map.of());
        assertEquals("{}", json);
        assertNull(GsonUtils.fromJson("null", Map.class));
    }

    // ---------- 下面 2 个用例记录当前缺陷，步骤 05 修复后应改为"能解析" ----------

    @Test
    @DisplayName("【已知缺陷】Markdown 代码围栏会导致解析失败")
    void fencedJsonFailsWithStrictParser() {
        String llmOutput = """
                ```json
                {"mainTitle":"标题"}
                ```
                """;
        // 严格模式（程序自己生成的 JSON）仍不容错，这是有意为之
        assertThrows(JsonSyntaxException.class, () -> GsonUtils.fromJson(llmOutput, Map.class));
    }

    @Test
    @DisplayName("【已知缺陷】JSON 前后带解释文字会解析失败")
    void jsonWithProseFailsWithStrictParser() {
        String llmOutput = "好的，以下是标题方案：\n{\"mainTitle\":\"标题\"}";
        assertThrows(JsonSyntaxException.class, () -> GsonUtils.fromJson(llmOutput, Map.class));
    }

    // ---------- 以下为步骤 05 新增：容错模式 ----------

    @Test
    @DisplayName("【已修复】容错模式能剥离代码围栏")
    void fencedJsonWorksWithLenientParser() {
        String llmOutput = """
                ```json
                {"mainTitle":"标题"}
                ```
                """;
        Map<?, ?> map = GsonUtils.fromLlmOutput(llmOutput, Map.class);
        assertEquals("标题", map.get("mainTitle"));
    }

    @Test
    @DisplayName("【已修复】容错模式能跳过前置解释文字")
    void jsonWithProseWorksWithLenientParser() {
        String llmOutput = "好的，以下是标题方案：\n{\"mainTitle\":\"标题\"}";
        Map<?, ?> map = GsonUtils.fromLlmOutput(llmOutput, Map.class);
        assertEquals("标题", map.get("mainTitle"));
    }

    @Test
    @DisplayName("容错模式遇到彻底无法解析的内容会抛专用异常")
    void lenientParserThrowsDedicatedException() {
        assertThrows(GsonUtils.LlmOutputParseException.class,
                () -> GsonUtils.fromLlmOutput("这不是 JSON，完全没有花括号", Map.class));
    }

    @Test
    @DisplayName("tryFromLlmOutput 失败时返回 null 而不抛异常")
    void tryLenientReturnsNull() {
        Object r = GsonUtils.tryFromLlmOutput("完全不是 JSON", Map.class);
        assertNull(r);
    }
}
