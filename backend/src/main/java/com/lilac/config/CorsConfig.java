package com.lilac.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * CORS 配置
 */
@Configuration
@Slf4j
public class CorsConfig implements WebMvcConfigurer {

    /**
     * 允许的源（逗号分隔）。
     * 默认仅本地开发端口：Vite 默认 5173，备用 5174。
     */
    @Value("${cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173,http://localhost:5174,http://127.0.0.1:5174}")
    private String allowedOrigins;

    /**
     * 是否允许携带凭证（Cookie）。
     * 本项目用 Redis Session 存登录态，必须为 true；
     * 但也正因如此，源白名单必须收紧。
     */
    @Value("${cors.allow-credentials:true}")
    private boolean allowCredentials;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> origins = parseOrigins(allowedOrigins);

        if (origins.isEmpty()) {
            // 配置为空时直接不放行任何跨域请求，避免退化成"全放开"
            log.warn("CORS 白名单为空，将拒绝所有跨域请求。请检查 cors.allowed-origins 配置");
            return;
        }

        log.info("CORS 白名单已加载: {} (allowCredentials={})", origins, allowCredentials);

        registry.addMapping("/**")
                .allowCredentials(allowCredentials)
                .allowedOrigins(origins.toArray(new String[0]))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                // 只放行实际用到的请求头，不用 "*"
                .allowedHeaders("Content-Type", "Authorization", "X-Requested-With", "Accept", "Origin")
                // 只暴露前端确实需要读取的响应头，不用 "*"
                .exposedHeaders("Content-Disposition")
                .maxAge(3600);
    }

    private List<String> parseOrigins(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .forEach(result::add);
        return result;
    }
}
