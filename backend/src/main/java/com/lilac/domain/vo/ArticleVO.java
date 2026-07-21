package com.lilac.domain.vo;

import com.lilac.domain.entity.Article;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文章VO
 */
@Data
public class ArticleVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    private Long id;

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 选题
     */
    private String topic;

    /**
     * 标题
     */
    private String mainTitle;

    /**
     * 副标题
     */
    private String subTitle;

    /**
     * 概要
     */
    private String outline;

    /**
     * 内容
     */
    private String content;

    /**
     * 全文
     */
    private String fullContent;

    /**
     * 封面图片
     */
    private String coverImage;

    /**
     * 图片
     */
    private String images;

    /**
     * 状态
     */
    private String status;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 完成时间
     */
    private LocalDateTime completedTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;


    /**
     * 对象转包装类
     *
     * @param article 文章
     * @return 文章VO
     */
    public static ArticleVO objToVO(Article article) {
        if (article == null) {
            return null;
        }
        ArticleVO articleVO = new ArticleVO();
        BeanUtils.copyProperties(article, articleVO);
        return articleVO;
     }
}
