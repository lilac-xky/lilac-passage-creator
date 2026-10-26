package com.lilac.unit;

import com.lilac.enums.ArticleStyleEnum;
import com.lilac.enums.ImageMethodEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 枚举查找逻辑单测。
 *
 * <p>这些枚举被 ImageServiceStrategy、ImageAnalyzerAgent 等核心链路依赖，
 * 且都是纯静态逻辑，不依赖 Spring 容器，适合作为 CI 的第一批冒烟测试。
 */
@DisplayName("枚举查找逻辑")
class EnumTest {

    // ---------- ImageMethodEnum ----------

    @Test
    @DisplayName("getByValue 能按值命中")
    void imageMethodGetByValue() {
        assertEquals(ImageMethodEnum.PEXELS, ImageMethodEnum.getByValue("PEXELS"));
        assertEquals(ImageMethodEnum.NANO_BANANA, ImageMethodEnum.getByValue("NANO_BANANA"));
        assertEquals(ImageMethodEnum.SVG_DIAGRAM, ImageMethodEnum.getByValue("SVG_DIAGRAM"));
    }

    @Test
    @DisplayName("getByValue 对未知值返回 null 而不是抛异常")
    void imageMethodGetByValueUnknown() {
        // resolveMethod() 依赖这个契约来做 fallback
        assertNull(ImageMethodEnum.getByValue(null));
        assertNull(ImageMethodEnum.getByValue(""));
        assertNull(ImageMethodEnum.getByValue("NOT_EXIST"));
    }

    @Test
    @DisplayName("降级方案唯一且是 PICSUM")
    void fallbackMethodIsUnique() {
        assertEquals(ImageMethodEnum.PICSUM, ImageMethodEnum.getFallbackMethod());

        long fallbackCount = 0;
        for (ImageMethodEnum m : ImageMethodEnum.values()) {
            if (m.isFallback()) {
                fallbackCount++;
            }
        }
        assertEquals(1, fallbackCount, "降级方案必须唯一，否则 handleFallback 行为不确定");
    }

    @Test
    @DisplayName("AI 生图与检索类方法的分类正确")
    void aiGeneratedFlag() {
        assertTrue(ImageMethodEnum.NANO_BANANA.isAiGenerated());
        assertTrue(ImageMethodEnum.MERMAID.isAiGenerated());
        assertTrue(ImageMethodEnum.SVG_DIAGRAM.isAiGenerated());

        assertFalse(ImageMethodEnum.PEXELS.isAiGenerated());
        assertFalse(ImageMethodEnum.ICONIFY.isAiGenerated());
        assertFalse(ImageMethodEnum.EMOJI_PACK.isAiGenerated());
    }

    @Test
    @DisplayName("每个枚举的 value 与 name() 一致，且能被 getByValue 反查")
    void imageMethodValueIsRoundTrippable() {
        for (ImageMethodEnum m : ImageMethodEnum.values()) {
            assertEquals(m.name(), m.getValue(), "value 与 name 必须一致，否则前端传参会错位");
            assertEquals(m, ImageMethodEnum.getByValue(m.getValue()));
        }
    }

    // ---------- ArticleStyleEnum ----------

    @Test
    @DisplayName("四种风格都能被识别")
    void styleGetEnumByValue() {
        assertEquals(ArticleStyleEnum.TECH, ArticleStyleEnum.getEnumByValue("tech"));
        assertEquals(ArticleStyleEnum.EMOTIONAL, ArticleStyleEnum.getEnumByValue("emotional"));
        assertEquals(ArticleStyleEnum.EDUCATIONAL, ArticleStyleEnum.getEnumByValue("educational"));
        assertEquals(ArticleStyleEnum.HUMOROUS, ArticleStyleEnum.getEnumByValue("humorous"));
    }

    @Test
    @DisplayName("空风格合法，未知风格不合法")
    void styleIsValid() {
        // 业务上允许不选风格
        assertTrue(ArticleStyleEnum.isValid(null));
        assertTrue(ArticleStyleEnum.isValid(""));
        assertTrue(ArticleStyleEnum.isValid("tech"));

        assertFalse(ArticleStyleEnum.isValid("unknown"));
        // 注意：风格值是全小写，大写不应命中
        assertFalse(ArticleStyleEnum.isValid("TECH"));
    }

    @Test
    @DisplayName("getValues 返回全部四个值")
    void styleGetValues() {
        assertEquals(4, ArticleStyleEnum.getValues().size());
        assertTrue(ArticleStyleEnum.getValues().contains("tech"));
    }
}
