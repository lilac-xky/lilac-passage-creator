package com.lilac.service;

import com.lilac.domain.dto.article.ArticleQueryRequest;
import com.lilac.domain.dto.article.ArticleState;
import com.lilac.domain.entity.Article;
import com.lilac.domain.entity.User;
import com.lilac.domain.vo.ArticleVO;
import com.lilac.enums.ArticleStatusEnum;
import com.mybatisflex.core.paginate.Page;

/**
 * 文章服务接口
 */
public interface ArticleService {

    /**
     * 创建文章生成任务
     *
     * @param topic  选题
     * @param loginUser 登录用户
     * @return 任务ID
     */
    String createArticleTask(String topic, User loginUser);

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
