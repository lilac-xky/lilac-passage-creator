package com.lilac.service;

import com.lilac.domain.dto.article.ArticleQueryRequest;
import com.lilac.domain.dto.article.ArticleState;
import com.lilac.domain.entity.Article;
import com.lilac.domain.entity.User;
import com.lilac.domain.vo.ArticleVO;
import com.lilac.enums.ArticleStatusEnum;
import com.mybatisflex.core.paginate.Page;

import java.util.List;

/**
 * 文章服务接口
 */
public interface ArticleService {

    /**
     * 创建文章生成任务
     *
     * @param topic  选题
     * @param style  风格
     * @param enabledImageMethods 启用的图片处理方法
     * @param loginUser 登录用户
     * @return 创建成功返回任务ID，失败返回 null
     */
    String createArticleTaskWithQuotaCheck(String topic, String style, List<String> enabledImageMethods, User loginUser);

    /**
     * 创建文章生成任务
     *
     * @param topic  选题
     * @param style  风格
     * @param enabledImageMethods 启用的图片处理方法
     * @param loginUser 登录用户
     * @return 创建成功返回任务ID，失败返回 null
     */
    String createArticleTask(String topic, String style, List<String> enabledImageMethods, User loginUser);

    /**
     * 根据任务ID获取文章信息
     *
     * @param taskId 任务ID
     * @return 文章信息
     */
    Article getByTaskId(String taskId);

    /**
     * 获取文章详情
     *
     * @param taskId      任务ID
     * @param loginUser 登录用户
     * @return 文章详情
     */
    ArticleVO getArticleDetail(String taskId, User loginUser);

    /**
     * 更新文章状态
     *
     * @param taskId      任务ID
     * @param status      状态
     * @param errorMessage 错误信息
     */
    void updateArticleStatus(String taskId, ArticleStatusEnum status, String errorMessage);

    /**
     * 保存文章内容
     *
     * @param taskId 任务ID
     * @param state  文章状态
     */
    void saveArticleContent(String taskId, ArticleState state);

    /**
     * 列表文章
     *
     * @param request 查询参数
     * @param loginUser 登录用户
     * @return 文章列表
     */
    Page<ArticleVO> listArticleByPage(ArticleQueryRequest request, User loginUser);

    /**
     * 删除文章
     *
     * @param id 文章ID
     * @param loginUser 登录用户
     * @return 是否成功
     */
    boolean deleteArticle(Long id, User loginUser);
}
