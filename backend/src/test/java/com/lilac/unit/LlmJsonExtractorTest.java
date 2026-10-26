package com.lilac.unit;

import com.lilac.utils.LlmJsonExtractor;
import com.lilac.utils.LlmJsonExtractor.ExtractResult;
import com.lilac.utils.LlmJsonExtractor.RepairStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LLM JSON 提取/修复逻辑单测。
 *
 * <p>覆盖真实调用中观察到的各类模型输出形态。
 * 每个用例的输入都是<b>实际会在生产里出现的</b>，不是构造的极端值。
 */
@DisplayName("LLM JSON 提取与修复")
class LlmJsonExtractorTest {

    // ---------- ① 无需修复 ----------

    @Nested
    @DisplayName("原文合法")
    class Valid {

        @Test
        @DisplayName("裸对象直接返回，策略为 NONE")
        void plainObject() {
            ExtractResult r = LlmJsonExtractor.extract("{\"a\":1}");
            assertEquals(RepairStrategy.NONE, r.strategy());
            assertEquals("{\"a\":1}", r.json());
        }

        @Test
        @DisplayName("裸数组直接返回，策略为 NONE")
        void plainArray() {
            ExtractResult r = LlmJsonExtractor.extract("[{\"mainTitle\":\"标题\"}]");
            assertEquals(RepairStrategy.NONE, r.strategy());
            assertTrue(r.json().startsWith("["));
        }

        @Test
        @DisplayName("首尾空白不影响判定")
        void surroundingWhitespace() {
            ExtractResult r = LlmJsonExtractor.extract("  \n {\"a\":1} \n ");
            assertEquals(RepairStrategy.NONE, r.strategy());
        }
    }

    // ---------- ② 代码围栏 ----------

    @Nested
    @DisplayName("Markdown 代码围栏")
    class Fence {

        @Test
        @DisplayName("```json 围栏")
        void jsonFence() {
            ExtractResult r = LlmJsonExtractor.extract("```json\n{\"a\":1}\n```");
            assertEquals(RepairStrategy.STRIP_FENCE, r.strategy());
            assertEquals("{\"a\":1}", r.json());
        }

        @Test
        @DisplayName("无语言标记的 ``` 围栏")
        void plainFence() {
            ExtractResult r = LlmJsonExtractor.extract("```\n{\"a\":1}\n```");
            assertEquals(RepairStrategy.STRIP_FENCE, r.strategy());
            assertEquals("{\"a\":1}", r.json());
        }

        @Test
        @DisplayName("大写 ```JSON 围栏")
        void uppercaseFence() {
            ExtractResult r = LlmJsonExtractor.extract("```JSON\n{\"a\":1}\n```");
            assertEquals(RepairStrategy.STRIP_FENCE, r.strategy());
        }

        @Test
        @DisplayName("围栏内是数组")
        void fenceWithArray() {
            ExtractResult r = LlmJsonExtractor.extract("```json\n[1,2,3]\n```");
            assertEquals(RepairStrategy.STRIP_FENCE, r.strategy());
            assertEquals("[1,2,3]", r.json());
        }
    }

    // ---------- ③ 混杂文字 ----------

    @Nested
    @DisplayName("夹杂解释文字")
    class Prose {

        @Test
        @DisplayName("前置解释")
        void leadingProse() {
            ExtractResult r = LlmJsonExtractor.extract("好的，以下是标题方案：\n{\"a\":1}");
            assertEquals(RepairStrategy.EXTRACT_BLOCK, r.strategy());
            assertEquals("{\"a\":1}", r.json());
        }

        @Test
        @DisplayName("后置说明")
        void trailingProse() {
            ExtractResult r = LlmJsonExtractor.extract("{\"a\":1}\n以上共 1 个方案，请选择。");
            assertEquals(RepairStrategy.EXTRACT_BLOCK, r.strategy());
            assertEquals("{\"a\":1}", r.json());
        }

        @Test
        @DisplayName("前后都有文字")
        void bothSides() {
            ExtractResult r = LlmJsonExtractor.extract("分析如下：{\"a\":1} 希望能帮到你");
            assertEquals(RepairStrategy.EXTRACT_BLOCK, r.strategy());
            assertEquals("{\"a\":1}", r.json());
        }

        @Test
        @DisplayName("嵌套对象能正确配对，不会截断")
        void nestedObject() {
            String input = "结果：{\"a\":{\"b\":{\"c\":1}},\"d\":2} 完成";
            ExtractResult r = LlmJsonExtractor.extract(input);
            assertEquals(RepairStrategy.EXTRACT_BLOCK, r.strategy());
            assertEquals("{\"a\":{\"b\":{\"c\":1}},\"d\":2}", r.json());
        }

        @Test
        @DisplayName("字符串里含花括号不会被误判为结构结束")
        void braceInsideString() {
            String input = "{\"content\":\"这里有 } 和 { 字符\",\"b\":2}";
            ExtractResult r = LlmJsonExtractor.extract(input);
            assertEquals("{\"content\":\"这里有 } 和 { 字符\",\"b\":2}", r.json());
        }

        @Test
        @DisplayName("字符串里含转义引号不会破坏配对")
        void escapedQuoteInsideString() {
            String input = "{\"content\":\"他说\\\"你好\\\"\",\"b\":2}";
            ExtractResult r = LlmJsonExtractor.extract(input);
            assertNotNull(r.json());
            assertEquals(RepairStrategy.NONE, r.strategy());
        }

        @Test
        @DisplayName("前置文字里含中文括号时优先取对象而非数组")
        void prefersObjectWhenObjectComesFirst() {
            ExtractResult r = LlmJsonExtractor.extract("（说明）{\"a\":1}");
            assertEquals("{\"a\":1}", r.json());
        }

        @Test
        @DisplayName("数组在前时取数组")
        void arrayFirst() {
            ExtractResult r = LlmJsonExtractor.extract("结果：[{\"a\":1}]");
            assertEquals(RepairStrategy.EXTRACT_BLOCK, r.strategy());
            assertEquals("[{\"a\":1}]", r.json());
        }
    }

    // ---------- ④ 标点规范化 ----------

    @Nested
    @DisplayName("非标准标点")
    class Normalize {

        @Test
        @DisplayName("全角冒号与逗号需要规范化")
        void fullWidthPunctuation() {
            ExtractResult r = LlmJsonExtractor.extract("{\"a\"：1，\"b\"：2}");
            assertEquals(RepairStrategy.NORMALIZE, r.strategy());
            assertEquals("{\"a\":1,\"b\":2}", r.json());
        }

        @Test
        @DisplayName("全角引号：Gson 宽松模式会当作无引号标识符接受，不报错也不需修复")
        void fullWidthQuotesHandledByGson() {
            // 实测 Gson 宽松模式把 “a” 解析成 key="a"，结果正确，因此 strategy 为 NONE
            ExtractResult r = LlmJsonExtractor.extract("{“a”:1}");
            assertEquals(RepairStrategy.NONE, r.strategy());
        }

        @Test
        @DisplayName("单引号：Gson 宽松模式直接支持")
        void singleQuotesHandledByGson() {
            ExtractResult r = LlmJsonExtractor.extract("{'a':1}");
            assertEquals(RepairStrategy.NONE, r.strategy());
        }

        @Test
        @DisplayName("对象的尾随逗号需要规范化")
        void trailingCommaObject() {
            ExtractResult r = LlmJsonExtractor.extract("{\"a\":1,}");
            assertEquals(RepairStrategy.NORMALIZE, r.strategy());
            assertEquals("{\"a\":1}", r.json());
        }

        @Test
        @DisplayName("【重要】数组尾随逗号必须规范化，否则 Gson 静默补 null 元素")
        void trailingCommaArrayMustNormalize() {
            // 实测：Gson 宽松模式把 [1,2,3,] 解析成 [1,2,3,null]，
            // 不报错但多出一个 null —— 这是静默数据污染，比抛异常更危险。
            // 因此 extract() 里对"原文合法但含尾随逗号"的情况主动规范化。
            ExtractResult r = LlmJsonExtractor.extract("[1,2,3,]");
            assertEquals(RepairStrategy.NORMALIZE, r.strategy());
            assertEquals("[1,2,3]", r.json());
        }

        @Test
        @DisplayName("对象内的数组尾随逗号同样规范化")
        void nestedArrayTrailingComma() {
            ExtractResult r = LlmJsonExtractor.extract("{\"list\":[1,2,]}");
            assertEquals(RepairStrategy.NORMALIZE, r.strategy());
            assertEquals("{\"list\":[1,2]}", r.json());
        }
    }

    // ---------- ⑤ 无法修复 ----------

    @Nested
    @DisplayName("无法修复")
    class Failed {

        @Test
        @DisplayName("null / 空串")
        void nullOrBlank() {
            assertEquals(RepairStrategy.FAILED, LlmJsonExtractor.extract(null).strategy());
            assertEquals(RepairStrategy.FAILED, LlmJsonExtractor.extract("").strategy());
            assertEquals(RepairStrategy.FAILED, LlmJsonExtractor.extract("   ").strategy());
        }

        @Test
        @DisplayName("完全没有 JSON 结构")
        void noJsonAtAll() {
            ExtractResult r = LlmJsonExtractor.extract("抱歉，我无法完成这个请求。");
            assertEquals(RepairStrategy.FAILED, r.strategy());
            assertNull(r.json());
        }

        @Test
        @DisplayName("括号不闭合（被截断）")
        void truncated() {
            ExtractResult r = LlmJsonExtractor.extract("{\"a\":1,\"b\":");
            assertEquals(RepairStrategy.FAILED, r.strategy());
        }

        @Test
        @DisplayName("裸字符串不算合法 JSON 结构")
        void bareStringRejected() {
            assertEquals(RepairStrategy.FAILED, LlmJsonExtractor.extract("\"just a string\"").strategy());
        }

        @Test
        @DisplayName("裸数字不算合法 JSON 结构")
        void bareNumberRejected() {
            assertEquals(RepairStrategy.FAILED, LlmJsonExtractor.extract("12345").strategy());
        }
    }

    // ---------- ⑥ 修复记录 ----------

    @Test
    @DisplayName("repaired() 能正确反映是否发生过修复")
    void repairedFlag() {
        assertTrue(!LlmJsonExtractor.extract("{\"a\":1}").repaired());
        assertTrue(LlmJsonExtractor.extract("```json\n{\"a\":1}\n```").repaired());
        assertTrue(LlmJsonExtractor.extract("说明：{\"a\":1}").repaired());
    }
}
