package com.lilac.unit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CORS 白名单配置单测。
 *
 * <p>安全修复（步骤 04）的核心是「不再放行 *」。
 * 这里通过反射直接调用 {@code parseOrigins} 与 {@code addCorsMappings}，
 * 不启动 Spring 容器即可验证白名单语义。
 */
@DisplayName("CORS 白名单")
class CorsConfigTest {

    private final com.lilac.config.CorsConfig config = new com.lilac.config.CorsConfig();

    private List<String> parse(String raw) throws Exception {
        Method m = com.lilac.config.CorsConfig.class.getDeclaredMethod("parseOrigins", String.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<String> r = (List<String>) m.invoke(config, raw);
        return r;
    }

    private CorsConfiguration buildAndGet(String origins) throws Exception {
        // 注入字段
        var field = com.lilac.config.CorsConfig.class.getDeclaredField("allowedOrigins");
        field.setAccessible(true);
        field.set(config, origins);

        var credField = com.lilac.config.CorsConfig.class.getDeclaredField("allowCredentials");
        credField.setAccessible(true);
        credField.set(config, true);

        CorsRegistry registry = new CorsRegistry();
        config.addCorsMappings(registry);

        // CorsRegistry 内部的 registrations 是私有的，用反射取出
        var regField = CorsRegistry.class.getDeclaredField("registrations");
        regField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var registrations = (ArrayList<?>) regField.get(registry);
        if (registrations.isEmpty()) {
            return null;
        }
        Object reg = registrations.get(0);
        var configField = reg.getClass().getDeclaredField("config");
        configField.setAccessible(true);
        return (CorsConfiguration) configField.get(reg);
    }

    @Test
    @DisplayName("逗号分隔的源能被正确拆分并去空格")
    void parseMultipleOrigins() throws Exception {
        List<String> origins = parse("http://a.com, http://b.com ,http://c.com");
        assertEquals(List.of("http://a.com", "http://b.com", "http://c.com"), origins);
    }

    @Test
    @DisplayName("空配置解析为空列表，不会退化成放行全部")
    void parseBlank() throws Exception {
        assertTrue(parse(null).isEmpty());
        assertTrue(parse("").isEmpty());
        assertTrue(parse("   ").isEmpty());
        assertTrue(parse(",,,").isEmpty());
    }

    @Test
    @DisplayName("默认配置只包含本地开发地址")
    void defaultOriginsAreLocalOnly() throws Exception {
        CorsConfiguration cfg = buildAndGet("http://localhost:5173,http://127.0.0.1:5173");
        List<String> allowed = cfg.getAllowedOrigins();

        assertEquals(2, allowed.size());
        assertTrue(allowed.contains("http://localhost:5173"));
        // 关键：绝不能出现通配符
        assertFalse(allowed.contains("*"), "allowedOrigins 不允许为 *（与 allowCredentials 组合会任意站放行）");
    }

    @Test
    @DisplayName("【回归】不再使用 allowedOriginPatterns(\"*\")")
    void noWildcardPattern() throws Exception {
        CorsConfiguration cfg = buildAndGet("http://localhost:5173");
        assertTrue(cfg.getAllowedOriginPatterns() == null || cfg.getAllowedOriginPatterns().isEmpty(),
                "改造后不应再设置 allowedOriginPatterns，否则通配符会绕过白名单");
    }

    @Test
    @DisplayName("【回归】allowedHeaders 不再为 *")
    void noWildcardHeaders() throws Exception {
        CorsConfiguration cfg = buildAndGet("http://localhost:5173");
        List<String> headers = cfg.getAllowedHeaders();
        assertFalse(headers.contains("*"), "allowedHeaders 不应为 *，应显式列出");
        assertTrue(headers.contains("Content-Type"));
    }

    @Test
    @DisplayName("【回归】exposedHeaders 不再为 *")
    void noWildcardExposedHeaders() throws Exception {
        CorsConfiguration cfg = buildAndGet("http://localhost:5173");
        List<String> exposed = cfg.getExposedHeaders();
        assertFalse(exposed.contains("*"), "exposedHeaders 不应为 *");
    }

    @Test
    @DisplayName("凭证放行与白名单同时存在（本项目用 Redis Session）")
    void credentialsTrueWithWhitelist() throws Exception {
        CorsConfiguration cfg = buildAndGet("http://localhost:5173");
        assertTrue(Boolean.TRUE.equals(cfg.getAllowCredentials()));
        assertEquals(List.of("http://localhost:5173"), cfg.getAllowedOrigins());
    }

    @Test
    @DisplayName("白名单为空时不注册任何映射（拒绝全部跨域）")
    void emptyWhitelistRegistersNothing() throws Exception {
        CorsConfiguration cfg = buildAndGet("");
        assertTrue(cfg == null, "白名单为空时不应注册 CORS 映射");
    }
}
