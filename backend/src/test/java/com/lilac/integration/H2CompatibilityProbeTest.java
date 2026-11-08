package com.lilac.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 探测：init.sql 能否直接在 H2（MySQL 兼容模式）中执行。
 *
 * <p>这是搭建 test profile 的第一步 —— 先用最小成本验证假设，
 * 而不是直接改配置然后对着启动失败日志猜。
 *
 * <p>结论会写入 docs/changes/19-test-profile.md。
 */
@DisplayName("探测：init.sql 在 H2 的兼容性")
class H2CompatibilityProbeTest {

    private static final String JDBC =
            "jdbc:h2:mem:probe_;MODE=MySQL;DATABASE_TO_LOWER=TRUE;"
                    + "CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1";

    /** 把 SQL 文件按分号拆成语句（忽略字符串内的分号） */
    private List<String> splitStatements(String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inString = false;
        boolean inLineComment = false;

        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            char next = i + 1 < sql.length() ? sql.charAt(i + 1) : 0;

            if (inLineComment) {
                if (c == '\n') {
                    inLineComment = false;
                    current.append(c);
                }
                continue;
            }
            if (!inString && c == '-' && next == '-') {
                inLineComment = true;
                i++;
                continue;
            }
            if (c == '\'') {
                inString = !inString;
                current.append(c);
                continue;
            }
            if (c == ';' && !inString) {
                String s = current.toString().trim();
                if (!s.isEmpty()) {
                    statements.add(s);
                }
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        String tail = current.toString().trim();
        if (!tail.isEmpty()) {
            statements.add(tail);
        }
        return statements;
    }

    @Test
    @DisplayName("逐条执行 init.sql，统计哪些语句 H2 不支持")
    void probe() throws Exception {
        Path initSql = Path.of("src/main/resources/db/init.sql");
        if (!Files.exists(initSql)) {
            // 在 target 下运行时路径不同，尝试另一种
            initSql = Path.of("../src/main/resources/db/init.sql");
        }
        assertTrue(Files.exists(initSql), "找不到 init.sql: " + initSql.toAbsolutePath());

        String sql = Files.readString(initSql, StandardCharsets.UTF_8);
        List<String> statements = splitStatements(sql);

        List<String> failed = new ArrayList<>();
        List<String> ok = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(JDBC, "sa", "");
             Statement st = conn.createStatement()) {
            for (String s : statements) {
                try {
                    st.execute(s);
                    ok.add(s);
                } catch (SQLException e) {
                    failed.add(firstLine(s) + "  ==>  " + e.getMessage().split("\n")[0]);
                }
            }
        }

        System.out.println("\n========== H2 兼容性探测结果 ==========");
        System.out.println("总语句数: " + statements.size());
        System.out.println("成功: " + ok.size() + ", 失败: " + failed.size());

        if (!failed.isEmpty()) {
            System.out.println("\n---------- 失败的语句 ----------");
            failed.forEach(f -> System.out.println("  " + f));
        }

        // 把成功的语句也输出，便于生成测试专用 schema
        System.out.println("\n---------- 成功的语句类型 ----------");
        ok.stream()
                .map(H2CompatibilityProbeTest::firstLine)
                .forEach(s -> System.out.println("  OK  " + s));

        System.out.println("======================================\n");

        // 同时落盘，避免 surefire 只在同 JVM 多 test 时丢失 system-out
        StringBuilder sb = new StringBuilder();
        sb.append("总语句数: ").append(statements.size()).append('\n');
        sb.append("成功: ").append(ok.size()).append(", 失败: ").append(failed.size()).append("\n\n");
        sb.append("=== 失败 ===\n");
        failed.forEach(f -> sb.append("  ").append(f).append('\n'));
        sb.append("\n=== 成功 ===\n");
        ok.stream().map(H2CompatibilityProbeTest::firstLine).forEach(s -> sb.append("  OK  ").append(s).append('\n'));
        Files.writeString(Path.of(System.getProperty("java.io.tmpdir"), "h2-probe-result.txt"),
                sb.toString(), StandardCharsets.UTF_8);
    }

    private static String firstLine(String s) {
        String t = s.trim().replaceAll("\\s+", " ");
        return t.length() > 90 ? t.substring(0, 90) + "..." : t;
    }

    @Test
    @DisplayName("验证：H2 是否支持 CREATE TABLE 里的 ENGINE/COLLATE/COMMENT 子句")
    void probeCreateTableClauses() throws Exception {
        try (Connection conn = DriverManager.getConnection(JDBC, "sa", "");
             Statement st = conn.createStatement()) {

            // 完整还原 article 表的结尾子句
            String full = """
                    CREATE TABLE IF NOT EXISTS `probe_t1`
                    (
                        `id`   bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
                        `name` varchar(64) NOT NULL COMMENT '名称'
                    ) ENGINE = InnoDB
                      DEFAULT CHARSET = utf8mb4
                      COLLATE = utf8mb4_unicode_ci COMMENT ='测试表'
                    """;
            st.execute(full);
            System.out.println("\n[探测] ENGINE/CHARSET/COLLATE/COMMENT 子句：H2 接受 ✅\n");
        } catch (SQLException e) {
            System.out.println("\n[探测] ENGINE/CHARSET/COLLATE/COMMENT 子句：H2 拒绝 ❌ -> " + e.getMessage() + "\n");
        }
    }

    @Test
    @DisplayName("验证：H2 是否支持 json 类型与视图")
    void probeJsonAndView() throws Exception {
        try (Connection conn = DriverManager.getConnection(JDBC, "sa", "");
             Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE `probe_t2` (`id` bigint, `data` json)");
            st.execute("CREATE OR REPLACE VIEW `probe_v2` AS SELECT `id` FROM `probe_t2`");
            System.out.println("\n[探测] json 类型 + CREATE OR REPLACE VIEW：H2 接受 ✅\n");
        } catch (SQLException e) {
            System.out.println("\n[探测] json / 视图：H2 拒绝 ❌ -> " + e.getMessage() + "\n");
        }
    }
}
