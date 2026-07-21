package com.lilac.domain.dto.article;

import lombok.Data;

/**
 * 创建文章请求参数
 */
@Data
public class ArticleCreateRequest {

    /**
     *选题
     */
    private String topic;
}
