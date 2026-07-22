package com.lilac.domain.dto.article;

import lombok.Data;

import java.util.List;

/**
 * 创建文章请求参数
 */
@Data
public class ArticleCreateRequest {

    /**
     *选题
     */
    private String topic;

    /**
     * 文章风格（可选）
     */
    private String style;

    private List<String> enabledImageMethods;
}
