package com.lilac.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 建表脚本一致性测试（步骤 G1）。
 *
 * <p><b>这个测试要解决的核心问题</b>
 *
 * <p>项目不用迁移框架，导致建表脚本有两份：
 * <ul>
 *   <li>{@code src/main/resources/db/init.sql} —— 生产用（MySQL）</li>
 *   <li>{@code src/test/resources/schema-test.sql} —— 测试用（H2）</li>
 * </ul>
 *
 * <p>两份独立维护，<b>必然迟早漂移</b>。E3 已经因此出过一次事故
 * （init.sql 落后 5 个迁移，新环境跑不起来）。
 *
 * <p>本测试直接解析两个 SQL 文件，比对<b>表清单</b>与<b>列清单</b>。
 * 任何一边加了字段而另一边没加，测试立刻失败并指出差异。
 *
 * <p>这是唯一能在"不用迁移工具"的前提下防止漂移的手段。
 */
@DisplayName("init.sql 与 schema-test.sql 一致性")
class SchemaDriftTest {

    /** 从 CREATE TABLE 块里提取「表名 -> 列名集合」 */
    private static final Pattern TABLE_BLOCK = Pattern.compile(
            "CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?`(\\w+)`\\s*\\((.*?)\\n\\)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern COLUMN_LINE = Pattern.compile(
            "^\\s*`(\\w+)`\\s+(bigint|int|tinyint|varchar|text|mediumtext|datetime|decimal|json)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    private static Path findFile(String relative) {
        Path p = Path.of(relative);
        if (Files.exists(p)) {
            return p;
        }
        // 在 target/test-classes 之类的位置运行时，回退到项目根
        Path alt = Path.of("..", relative);
        if (Files.exists(alt)) {
            return alt;
        }
        throw new IllegalStateException("找不到文件: " + relative + " (尝试了 " + p.toAbsolutePath() + ")");
    }

    private static java.util.Map<String, Set<String>> parseTables(Path file) throws IOException {
        String sql = Files.readString(file, StandardCharsets.UTF_8);
        java.util.Map<String, Set<String>> result = new java.util.LinkedHashMap<>();

        Matcher tm = TABLE_BLOCK.matcher(sql);
        while (tm.find()) {
            String table = tm.group(1).toLowerCase();
            String body = tm.group(2);
            Set<String> columns = new LinkedHashSet<>();
            Matcher cm = COLUMN_LINE.matcher(body);
            while (cm.find()) {
                columns.add(cm.group(1));
            }
            result.put(table, columns);
        }
        return result;
    }

    @Test
    @DisplayName("两份脚本的表清单必须一致")
    void tableListsMatch() throws Exception {
        var init = parseTables(findFile("src/main/resources/db/init.sql"));
        var test = parseTables(findFile("src/test/resources/schema-test.sql"));

        assertEquals(new LinkedHashSet<>(init.keySet()), new LinkedHashSet<>(test.keySet()),
                "表清单不一致：init.sql=" + init.keySet() + ", schema-test.sql=" + test.keySet());
        assertTrue(init.size() >= 5, "至少应有 5 张表，实际解析到 " + init.size() + "，可能是解析规则失效");
    }

    @Test
    @DisplayName("每个表的列清单必须一致（缺失即失败）")
    void columnListsMatch() throws Exception {
        var init = parseTables(findFile("src/main/resources/db/init.sql"));
        var test = parseTables(findFile("src/test/resources/schema-test.sql"));

        List<String> problems = new ArrayList<>();

        for (var entry : init.entrySet()) {
            String table = entry.getKey();
            Set<String> initCols = entry.getValue();
            Set<String> testCols = test.get(table);

            if (testCols == null) {
                problems.add(table + ": schema-test.sql 里没有这张表");
                continue;
            }

            // init.sql 有、schema-test.sql 没有
            for (String c : initCols) {
                if (!testCols.contains(c)) {
                    problems.add(table + "." + c + ": init.sql 有，schema-test.sql 缺失");
                }
            }
            // schema-test.sql 有、init.sql 没有（同样危险：测试通过但生产会挂）
            for (String c : testCols) {
                if (!initCols.contains(c)) {
                    problems.add(table + "." + c + ": schema-test.sql 有，init.sql 缺失");
                }
            }
        }

        assertTrue(problems.isEmpty(),
                "建表脚本已漂移，请同步两份文件：\n  " + String.join("\n  ", problems));
    }

    @Test
    @DisplayName("关键字表（user）在两边都被正确解析")
    void reservedWordTableParsed() throws Exception {
        var init = parseTables(findFile("src/main/resources/db/init.sql"));
        var test = parseTables(findFile("src/test/resources/schema-test.sql"));

        assertTrue(init.containsKey("user"), "init.sql 应解析出 user 表");
        assertTrue(test.containsKey("user"), "schema-test.sql 应解析出 user 表");
        assertTrue(init.get("user").contains("userAccount"), "应解析出驼峰列名 userAccount");
    }

    @Test
    @DisplayName("两个视图在 schema-test.sql 里都存在")
    void viewsPresent() throws Exception {
        String test = Files.readString(findFile("src/test/resources/schema-test.sql"), StandardCharsets.UTF_8);
        assertTrue(test.contains("v_prompt_effect"), "schema-test.sql 缺 v_prompt_effect");
        assertTrue(test.contains("v_image_degradation"), "schema-test.sql 缺 v_image_degradation");
    }

    @Test
    @DisplayName("schema-test.sql 不含 H2 不支持的语句")
    void noIncompatibleStatements() throws Exception {
        String test = Files.readString(findFile("src/test/resources/schema-test.sql"), StandardCharsets.UTF_8);
        String sqlOnly = test.lines()
                .filter(l -> !l.trim().startsWith("--"))
                .reduce("", (a, b) -> a + b + "\n");

        assertTrue(!sqlOnly.toUpperCase().contains("CREATE DATABASE"),
                "schema-test.sql 不应含 CREATE DATABASE（H2 不支持）");
        assertTrue(!sqlOnly.toUpperCase().contains("\nUSE "),
                "schema-test.sql 不应含 USE 语句（H2 不支持）");
    }
}
