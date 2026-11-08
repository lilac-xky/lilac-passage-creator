package com.lilac.tools;

import com.lilac.constant.PromptConstant;

import java.io.Writer;
import java.io.OutputStreamWriter;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 一次性工具：把 PromptConstant 的常量导出为 SQL 插入语句。
 *
 * <p>不部署到生产，仅在改造期用于生成 prompt_template 的初始化数据，
 * 避免手工复制 319 行文本块导致转义错误。
 *
 * <p>用法：在测试或临时 main 里调用 {@link #main(String[])}，
 * 输出到临时文件，再人工检查后并入 V3 脚本。
 */
public final class PromptExportTool {

    /** 常量名 → (agentName, description) */
    private static final Map<String, String[]> META = new LinkedHashMap<>();

    static {
        META.put("AGENT1_TITLE_PROMPT", new String[]{"TITLE", "智能体1：生成标题方案"});
        META.put("AGENT2_OUTLINE_PROMPT", new String[]{"OUTLINE", "智能体2：生成大纲"});
        META.put("AGENT2_DESCRIPTION_SECTION", new String[]{"OUTLINE", "智能体2：用户补充描述片段"});
        META.put("AGENT3_CONTENT_PROMPT", new String[]{"CONTENT", "智能体3：生成正文"});
        META.put("AGENT4_IMAGE_REQUIREMENTS_PROMPT", new String[]{"IMAGE", "智能体4：分析配图需求"});
        META.put("SVG_DIAGRAM_GENERATION_PROMPT", new String[]{"SVGD", "SVG 概念示意图生成"});
        META.put("AI_MODIFY_OUTLINE_PROMPT", new String[]{"OUTLINE_MODIFY", "AI 修改大纲"});
        META.put("STYLE_TECH_PROMPT", new String[]{"STYLE", "科技风格附加提示"});
        META.put("STYLE_EMOTIONAL_PROMPT", new String[]{"STYLE", "情感风格附加提示"});
        META.put("STYLE_EDUCATIONAL_PROMPT", new String[]{"STYLE", "教育风格附加提示"});
        META.put("STYLE_HUMOROUS_PROMPT", new String[]{"STYLE", "幽默风格附加提示"});
    }

    private static final Pattern VAR = Pattern.compile("\\{([a-zA-Z][a-zA-Z0-9]*)}");

    public static void main(String[] args) throws Exception {
        String out = args.length > 0 ? args[0] : "prompt_seed.sql";
        StringBuilder sb = new StringBuilder();
        sb.append("-- 由 PromptExportTool 自动生成，请勿手工编辑\n\n");

        for (Map.Entry<String, String[]> e : META.entrySet()) {
            String name = e.getKey();
            String agentName = e.getValue()[0];
            String desc = e.getValue()[1];
            String content = readConstant(name);
            if (content == null) {
                sb.append("-- ❌ 未找到常量: ").append(name).append("\n\n");
                continue;
            }
            String vars = extractVars(content);

            sb.append("INSERT INTO `prompt_template`\n")
              .append("    (`name`, `version`, `agentName`, `description`, `content`, `variables`, `enabled`, `abWeight`)\n")
              .append("VALUES ('").append(name).append("', 1, '").append(agentName).append("', '")
              .append(desc).append("', '")
              .append(escapeSql(content)).append("', ")
              .append(vars == null ? "NULL" : "'" + vars + "'").append(", 1, 100)\n")
              .append("ON DUPLICATE KEY UPDATE `content` = VALUES(`content`), `variables` = VALUES(`variables`);\n\n");
        }

        try (Writer w = new OutputStreamWriter(new FileOutputStream(out), StandardCharsets.UTF_8)) {
            w.write(sb.toString());
        }
        System.out.println("written: " + out);
    }

    /** 用反射读取接口里的 String 常量 */
    private static String readConstant(String name) {
        try {
            java.lang.reflect.Field f = PromptConstant.class.getField(name);
            Object v = f.get(null);
            return v == null ? null : v.toString();
        } catch (Exception ex) {
            return null;
        }
    }

    /** 提取占位符变量名，去重并保持出现顺序，逗号拼接 */
    static String extractVars(String content) {
        Matcher m = VAR.matcher(content);
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
        while (m.find()) {
            set.add(m.group(1));
        }
        return set.isEmpty() ? null : String.join(",", set);
    }

    /** MySQL 单引号转义 */
    static String escapeSql(String s) {
        return s.replace("\\", "\\\\")
                .replace("'", "''")
                .replace("\r", "");
    }
}
