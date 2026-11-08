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
     * 配图总数（步骤 E2）
     */
    private Integer imageTotal;

    /**
     * 其中降级到兜底图源（PICSUM 占位图）的数量（步骤 E2）
     *
     * <p>实测有过 10 张图里 5 张降级的案例（50%），但文章状态仍显示「已完成」，
     * 用户在页面上完全看不出来。暴露该字段后前端可以提示：
     * 「本文 N 张配图使用了占位图」。
     */
    private Integer imageFallbackCount;

    /**
     * 是否存在配图降级（步骤 E2，便于前端直接判断，不用自己算）
     */
    private Boolean hasImageFallback;

    /**
     * 配图降级率（百分比，0~100，步骤 E2）
     */
    private Integer imageFallbackRate;

    /**
     * 是否为严重降级（降级率 >= 30%，步骤 E2）
     *
     * <p>前端可据此展示更醒目的提示，例如：
     * 「本文 10 张配图中 5 张使用了占位图，建议重新生成」
     */
    private Boolean imageDegraded;

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

        // 降级标识（步骤 E2）：让前端不用自己判断
        // 历史数据可能为 null，统一兜底为 0/false
        int total = article.getImageTotal() == null ? 0 : article.getImageTotal();
        int fallback = article.getImageFallbackCount() == null ? 0 : article.getImageFallbackCount();
        articleVO.setImageTotal(total);
        articleVO.setImageFallbackCount(fallback);
        articleVO.setHasImageFallback(fallback > 0);
        articleVO.setImageFallbackRate(total == 0 ? 0 : (fallback * 100 / total));
        articleVO.setImageDegraded(article.getImageDegraded() != null && article.getImageDegraded() == 1);

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
