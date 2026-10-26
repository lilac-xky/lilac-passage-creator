package com.lilac.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Type;

/**
 * JSON 工具类
 */
@Slf4j
public final class GsonUtils {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private GsonUtils() {
    }

    public static String toJson(Object value) {
        return GSON.toJson(value);
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        return GSON.fromJson(json, clazz);
    }

    public static <T> T fromJson(String json, TypeToken<T> typeToken) {
        return GSON.fromJson(json, typeToken.getType());
    }

    public static <T> T fromJson(String json, Type type) {
        return GSON.fromJson(json, type);
    }

    // region LLM 输出容错解析

    /**
     * 解析 LLM 输出为对象。
     *
     * @param raw   模型原始输出（不可信，可能带代码围栏/解释文字）
     * @param clazz 目标类型
     * @return 解析结果；无法解析时抛 {@link LlmOutputParseException}
     */
    public static <T> T fromLlmOutput(String raw, Class<T> clazz) {
        T result = tryFromLlmOutput(raw, clazz);
        if (result == null) {
            throw new LlmOutputParseException(
                    "LLM 输出无法解析为 " + clazz.getSimpleName() + "，原始内容前 300 字符: " + preview(raw));
        }
        return result;
    }

    /**
     * 解析 LLM 输出为泛型集合（如 {@code List<TitleOption>}）。
     */
    public static <T> T fromLlmOutput(String raw, TypeToken<T> typeToken) {
        T result = tryFromLlmOutput(raw, typeToken);
        if (result == null) {
            throw new LlmOutputParseException(
                    "LLM 输出无法解析为 " + typeToken.getType() + "，原始内容前 300 字符: " + preview(raw));
        }
        return result;
    }

    /**
     * 容错解析，失败返回 {@code null} 而不抛异常。
     */
    public static <T> T tryFromLlmOutput(String raw, Class<T> clazz) {
        LlmJsonExtractor.ExtractResult extracted = LlmJsonExtractor.extract(raw);
        if (extracted.strategy() == LlmJsonExtractor.RepairStrategy.FAILED) {
            return null;
        }
        logIfRepaired(extracted);
        try {
            return GSON.fromJson(extracted.json(), clazz);
        } catch (JsonSyntaxException e) {
            log.warn("JSON 提取成功但反序列化失败, strategy={}, error={}",
                    extracted.strategy(), e.getMessage());
            return null;
        }
    }

    /**
     * 容错解析（泛型版），失败返回 {@code null} 而不抛异常。
     */
    public static <T> T tryFromLlmOutput(String raw, TypeToken<T> typeToken) {
        LlmJsonExtractor.ExtractResult extracted = LlmJsonExtractor.extract(raw);
        if (extracted.strategy() == LlmJsonExtractor.RepairStrategy.FAILED) {
            return null;
        }
        logIfRepaired(extracted);
        try {
            return GSON.fromJson(extracted.json(), typeToken.getType());
        } catch (JsonSyntaxException e) {
            log.warn("JSON 提取成功但反序列化失败, strategy={}, error={}",
                    extracted.strategy(), e.getMessage());
            return null;
        }
    }

    private static void logIfRepaired(LlmJsonExtractor.ExtractResult extracted) {
        if (extracted.repaired()) {
            // 这条日志是有价值的埋点：能统计模型输出不规范的比例
            log.info("LLM 输出经修复后解析成功, strategy={}", extracted.strategy().getDesc());
        }
    }

    private static String preview(String raw) {
        if (raw == null) {
            return "null";
        }
        String t = raw.trim();
        return t.length() > 300 ? t.substring(0, 300) + "..." : t;
    }

    // endregion

    /**
     * LLM 输出解析失败时抛出的专用异常。
     * 继承 {@link RuntimeException}，与既有的 {@code BusinessException} 区分开，
     * 便于上层按类型做重试 / 降级决策。
     */
    public static class LlmOutputParseException extends RuntimeException {
        public LlmOutputParseException(String message) {
            super(message);
        }

        public LlmOutputParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
