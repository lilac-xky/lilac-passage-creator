package com.lilac.domain.dto.article;

import com.lilac.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 文章查询参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ArticleQueryRequest extends PageRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    private Long id;

    /**
     * 用户id
     */
    private Long userId;

    /**
     * 状态
     */
    private String status;

}
