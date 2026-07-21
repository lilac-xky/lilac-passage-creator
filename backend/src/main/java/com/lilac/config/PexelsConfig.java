package com.lilac.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Pexels 配置
 */
@Configuration
@ConfigurationProperties(prefix = "pexels")
@Data
public class PexelsConfig {

    private String apiKey;
}
