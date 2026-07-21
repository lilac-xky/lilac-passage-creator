package com.lilac.service.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lilac.config.PexelsConfig;
import com.lilac.enums.ImageMethodEnum;
import com.lilac.service.ImageSearchService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static com.lilac.constant.ArticleConstant.*;

/**
 * Pexels 图片检索服务
 */
@Service
@Slf4j
public class PexelsService implements ImageSearchService {

    @Resource
    private PexelsConfig pexelsConfig;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    /**
     * 根据关键词搜索图片
     *
     * @param keywords 关键词
     * @return 图片 URL，未找到返回 null
     */
    @Override
    public String searchImage(String keywords) {
        if (pexelsConfig.getApiKey() == null || pexelsConfig.getApiKey().isBlank()) {
            log.warn("PEXELS_API_KEY is not configured, skipping image search");
            return null;
        }
        try {
            String url = buildSearchUrl(keywords);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("Authorization", pexelsConfig.getApiKey())
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("Pexels API 调用失败: {}", response.statusCode());
                return null;
            }
            return extractImageUrl(response.body(), keywords);
        } catch (IOException e) {
            log.error("Pexels API 调用异常", e);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Pexels API 调用被中断", e);
            return null;
        }
    }

    /**
     * 获取图片检索方法
     *
     * @return 图片检索方法枚举
     */
    @Override
    public ImageMethodEnum getMethod() {
        return ImageMethodEnum.PEXELS;
    }

    /**
     * 获取图片的 fallback 地址
     *
     * @param position 图片位置
     * @return 图片的 fallback 地址
     */
    @Override
    public String getFallbackImage(int position) {
        return String.format(PICSUM_URL_TEMPLATE, position);
    }

    /**
     * 构建搜索 URL
     *
     * @param keywords 搜索关键词
     * @return 完整的搜索 URL
     */
    private String buildSearchUrl(String keywords) {
        return String.format("%s?query=%s&per_page=%d&orientation=%s",
                PEXELS_API_URL,
                URLEncoder.encode(keywords, StandardCharsets.UTF_8),
                PEXELS_PER_PAGE,
                PEXELS_ORIENTATION_LANDSCAPE);
    }

    /**
     * 从响应中提取图片 URL
     *
     * @param responseBody 响应体
     * @param keywords     搜索关键词（用于日志）
     * @return 图片 URL，未找到返回 null
     */
    private String extractImageUrl(String responseBody, String keywords) {
        JsonObject jsonObject = JsonParser.parseString(responseBody).getAsJsonObject();
        JsonArray photos = jsonObject.getAsJsonArray("photos");
        
        if (photos.isEmpty()) {
            log.warn("Pexels 未检索到图片: {}", keywords);
            return null;
        }

        JsonObject photo = photos.get(0).getAsJsonObject();
        JsonObject src = photo.getAsJsonObject("src");
        return src.get("large").getAsString();
    }

}
