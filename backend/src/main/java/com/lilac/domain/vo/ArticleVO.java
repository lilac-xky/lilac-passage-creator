package com.lilac.domain.vo;

import com.lilac.domain.entity.Article;
import com.lilac.domain.dto.article.ArticleState;
import com.lilac.utils.GsonUtils;
import com.google.gson.reflect.TypeToken;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

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
    private List<ArticleState.OutlineSection> outline;

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
    private List<ArticleState.ImageResult> images;

    /**
     * 状态
     */
    private String status;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 文章风格
     */
    private String style;

    /**
     * 用户补充描述
     */
    private String userDescription;

    /**
     * 允许使用的配图方式
     */
    private List<String> enabledImageMethods;

    /**
     * 标题方案
     */
    private List<ArticleState.TitleOption> titleOptions;

    /**
     * 当前生成阶段
     */
    private String phase;

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
        articleVO.setOutline(parseList(article.getOutline(), new TypeToken<List<ArticleState.OutlineSection>>() {}));
        articleVO.setImages(parseList(article.getImages(), new TypeToken<List<ArticleState.ImageResult>>() {}));
        articleVO.setEnabledImageMethods(parseList(article.getEnabledImageMethods(), new TypeToken<List<String>>() {}));
        articleVO.setTitleOptions(parseList(article.getTitleOptions(), new TypeToken<List<ArticleState.TitleOption>>() {}));
        return articleVO;
     }

    private static <T> List<T> parseList(String json, TypeToken<List<T>> typeToken) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        List<T> result = GsonUtils.fromJson(json, typeToken);
        return result == null ? Collections.emptyList() : result;
    }
}
