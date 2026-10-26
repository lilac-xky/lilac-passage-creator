package com.lilac.utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;

/**
 * LLM 输出的 JSON 提取与修复工具
 */
@Slf4j
public final class LlmJsonExtractor {

    private LlmJsonExtractor() {
    }

    /**
     * 提取结果，包含原始文本与修复过程记录，便于排查与埋点。
     */
    public record ExtractResult(String json, RepairStrategy strategy) {
        public boolean repaired() {
            return strategy != RepairStrategy.NONE;
        }
    }

    /**
     * 修复策略，按尝试顺序排列。
     * 记录策略是为了能统计「模型输出有多不规范」——这是可以写进简历的数据。
     */
    public enum RepairStrategy {
        /** 原文就是合法 JSON，未做任何处理 */
        NONE("直接解析"),
        /** 剥离了 Markdown 代码围栏 */
        STRIP_FENCE("剥离代码围栏"),
        /** 从混杂文本中截取了 JSON 主体 */
        EXTRACT_BLOCK("截取 JSON 主体"),
        /** 修正了全角标点 / 单引号 / 尾随逗号 */
        NORMALIZE("规范化标点与逗号"),
        /** 以上都失败 */
        FAILED("无法修复");

        private final String desc;

        RepairStrategy(String desc) {
            this.desc = desc;
        }

        public String getDesc() {
            return desc;
        }
    }

    /**
     * 从 LLM 原始输出中提取可解析的 JSON 文本。
     *
     * @param raw 模型原始输出
     * @return 提取结果；无法修复时 {@code strategy == FAILED} 且 {@code json == null}
     */
    public static ExtractResult extract(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ExtractResult(null, RepairStrategy.FAILED);
        }

        String text = raw.trim();

        // ① 原文即合法 JSON
        //    但 Gson 的宽松模式有个坑：[1,2,3,] 会被静默解析成 [1,2,3,null]，
        //    不报错却多出 null 元素 —— 这是静默数据污染，比抛异常更危险。
        //    所以即使能解析，也要先检查尾随逗号并规范化。
        if (isValidJson(text)) {
            if (hasTrailingComma(text)) {
                String fixed = normalizePunctuation(text);
                if (isValidJson(fixed)) {
                    return new ExtractResult(fixed, RepairStrategy.NORMALIZE);
                }
            }
            return new ExtractResult(text, RepairStrategy.NONE);
        }

        // ② 剥离 Markdown 代码围栏
        String unfenced = stripCodeFence(text);
        if (!unfenced.equals(text)) {
            if (isValidJson(unfenced)) {
                return new ExtractResult(unfenced, RepairStrategy.STRIP_FENCE);
            }
            text = unfenced;
        }

        // ③ 从混杂文本中截取第一个完整的 JSON 对象/数组
        String block = extractJsonBlock(text);
        if (block != null && isValidJson(block)) {
            return new ExtractResult(block, RepairStrategy.EXTRACT_BLOCK);
        }

        // ④ 对截取结果做标点规范化后再试
        if (block != null) {
            String normalized = normalizePunctuation(block);
            if (isValidJson(normalized)) {
                return new ExtractResult(normalized, RepairStrategy.NORMALIZE);
            }
        }

        // 兜底：对整段做规范化
        String normalizedWhole = normalizePunctuation(text);
        if (isValidJson(normalizedWhole)) {
            return new ExtractResult(normalizedWhole, RepairStrategy.NORMALIZE);
        }

        log.warn("LLM 输出无法提取为 JSON，前 200 字符: {}",
                text.length() > 200 ? text.substring(0, 200) + "..." : text);
        return new ExtractResult(null, RepairStrategy.FAILED);
    }

    // region 内部实现

    private static boolean isValidJson(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        try {
            JsonElement el = JsonParser.parseString(text);
            // 只接受对象或数组；裸字符串/数字视为无效（业务上不该出现）
            return el.isJsonObject() || el.isJsonArray();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 判断是否含尾随逗号（{@code ,} 紧跟 {@code }} 或 {@code ]}）。
     *
     * <p>必须做这个检查，因为 Gson 宽松模式下
     * {@code [1,2,3,]} → {@code [1,2,3,null]}，会静默污染数据。
     * 只检查字符串外部，避免把正文里的 ",}" 误判。
     */
    static boolean hasTrailingComma(String json) {
        if (json == null) {
            return false;
        }
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < json.length() - 1; i++) {
            char c = json.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                if (inString) {
                    escaped = true;
                }
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (c == ',') {
                // 向后跳过空白，看下一个非空白字符
                for (int j = i + 1; j < json.length(); j++) {
                    char n = json.charAt(j);
                    if (n == '}' || n == ']') {
                        return true;
                    }
                    if (!Character.isWhitespace(n)) {
                        break;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 剥离 Markdown 代码围栏。
     * 支持 ```json / ```JSON / ``` 三种开头，以及结尾的 ```。
     */
    static String stripCodeFence(String text) {
        String t = text.trim();
        if (!t.startsWith("```")) {
            return t;
        }
        // 去掉起始围栏连同语言标记
        int firstNewline = t.indexOf('\n');
        if (firstNewline < 0) {
            return t;
        }
        String body = t.substring(firstNewline + 1);
        // 去掉结尾围栏
        int end = body.lastIndexOf("```");
        if (end >= 0) {
            body = body.substring(0, end);
        }
        return body.trim();
    }

    /**
     * 从混杂文本中截取第一个"看起来完整"的 JSON 对象或数组。
     *
     * <p>用括号配对计数（考虑字符串与转义），而不是正则 ——
     * 正文里出现 `{` 或 `}` 的中文内容很多，正则匹配会截错。
     */
    static String extractJsonBlock(String text) {
        int objStart = text.indexOf('{');
        int arrStart = text.indexOf('[');

        int start;
        char open;
        char close;
        if (objStart >= 0 && (arrStart < 0 || objStart < arrStart)) {
            start = objStart;
            open = '{';
            close = '}';
        } else if (arrStart >= 0) {
            start = arrStart;
            open = '[';
            close = ']';
        } else {
            return null;
        }

        int depth = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                if (inString) {
                    escaped = true;
                }
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (c == open) {
                depth++;
            } else if (c == close) {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    /**
     * 规范化常见的中文/非标准 JSON 写法。
     * 只做确定的字符替换，不做结构性补全。
     */
    static String normalizePunctuation(String json) {
        if (json == null) {
            return null;
        }
        String s = json;
        // 全角引号（在字符串外出现时才有意义，但这里保守替换）
        s = s.replace('“', '"').replace('”', '"');
        // 全角冒号与逗号（JSON 结构分隔符）
        s = s.replace('：', ':').replace('，', ',');
        // 去掉尾随逗号：,, 或 ,}
        s = s.replaceAll(",\\s*}", "}").replaceAll(",\\s*]", "]");
        return s.trim();
    }

    // endregion
}
