package com.lilac.domain.dto.article;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 确认文章大纲的请求参数
 */
@Data
public class ArticleConfirmOutlineRequest implements Serializable {

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 用户编辑后的大纲
     */
    private List<ArticleState.OutlineSection> outline;

    private static final long serialVersionUID = 1L;
}