package com.lilac.service.impl;

import cn.hutool.core.util.IdUtil;
import com.google.gson.reflect.TypeToken;
import com.lilac.domain.dto.article.ArticleQueryRequest;
import com.lilac.domain.dto.article.ArticleState;
import com.lilac.domain.entity.Article;
import com.lilac.domain.entity.User;
import com.lilac.domain.vo.ArticleVO;
import com.lilac.enums.ArticlePhaseEnum;
import com.lilac.enums.ArticleStatusEnum;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.exception.BusinessException;
import com.lilac.mapper.ArticleMapper;
import com.lilac.service.ArticleAgentService;
import com.lilac.service.ArticleService;
import com.lilac.service.QuotaService;
import com.lilac.utils.GsonUtils;
import com.lilac.utils.ThrowUtils;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static com.lilac.constant.UserConstant.ADMIN_ROLE;

/**
 * 文章服务实现类
 */
@Service
@Slf4j
public class ArticleServiceImpl extends ServiceImpl<ArticleMapper, Article> implements ArticleService {

    @Resource
    private QuotaService quotaService;
    @Resource
    private ArticleAgentService articleAgentService;

    /**
     * 创建文章生成任务
     *
     * @param topic  选题
     * @param style  风格
     * @param enabledImageMethods 启用的图片处理方法
     * @param loginUser 登录用户
     * @return 创建成功返回任务ID，失败返回 null
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createArticleTaskWithQuotaCheck(String topic, String style, List<String> enabledImageMethods, User loginUser) {
        // 在同一事务中：先扣配额，再创建任务
        quotaService.hasQuota(loginUser);
        // 如果任务创建失败，配额会自动回滚
        quotaService.checkAndConsumeQuota(loginUser);
        return createArticleTask(topic, style, enabledImageMethods, loginUser);
    }

    /**
     * 创建文章任务
     *
     * @param topic  选题
     * @param loginUser 登录用户
     * @return 任务ID
     */
    @Override
    public String createArticleTask(String topic, String style, List<String> enabledImageMethods, User loginUser) {
        // 生成任务ID
        String taskId = IdUtil.simpleUUID();
        // 创建文章记录
        Article article = new Article();
        article.setTaskId(taskId);
        article.setUserId(loginUser.getId());
        article.setTopic(topic);
        article.setStyle(style);
        article.setEnabledImageMethods(GsonUtils.toJson(enabledImageMethods));
        article.setStatus(ArticleStatusEnum.PENDING.getValue());
        article.setCreateTime(LocalDateTime.now());
        this.save(article);
        log.info("文章任务已创建, taskId={}, userId={}", taskId, loginUser.getId());
        return taskId;
    }

    /**
     * 根据任务ID获取文章记录
     *
     * @param taskId 任务ID
     * @return 文章记录
     */
    @Override
    public Article getByTaskId(String taskId) {
        return this.getOne(QueryWrapper.create().eq("taskId", taskId));
    }

    /**
     * 获取文章详情
     *
     * @param taskId 任务ID
     * @param loginUser 登录用户
     * @return 文章详情
     */
    @Override
    public ArticleVO getArticleDetail(String taskId, User loginUser) {
        Article article = getByTaskId(taskId);
        ThrowUtils.throwIf(article == null, HttpsCodeEnum.NOT_FOUND_ERROR);
        checkArticlePermission(article, loginUser);
        return ArticleVO.objToVO(article);
    }

    /**
     * 更新文章状态
     *
     * @param taskId      任务ID
     * @param status      状态
     * @param errorMessage 错误信息
     */
    @Override
    public void updateArticleStatus(String taskId, ArticleStatusEnum status, String errorMessage) {
        Article article = getByTaskId(taskId);
        if (article == null) {
            log.error("文章记录不存在, taskId={}", taskId);
            return;
        }
        article.setStatus(status.getValue());
        article.setErrorMessage(errorMessage);
        this.updateById(article);
        log.info("文章状态已更新, taskId={}, status={}", taskId, status.getValue());
    }

    /**
     * 保存文章内容
     *
     * @param taskId 任务ID
     * @param state  文章状态
     */
    @Override
    public void saveArticleContent(String taskId, ArticleState state) {
        Article article = getByTaskId(taskId);
        if (article == null) {
            log.error("文章记录不存在, taskId={}", taskId);
            return;
        }
        article.setMainTitle(state.getTitle().getMainTitle());
        article.setSubTitle(state.getTitle().getSubTitle());
        article.setOutline(GsonUtils.toJson(state.getOutline().getSections()));
        article.setContent(state.getContent());
        article.setFullContent(state.getFullContent());

        // 保存封面图 URL（从 images 列表中提取 position=1 的 URL）
        if (state.getImages() != null && !state.getImages().isEmpty()) {
            ArticleState.ImageResult cover = state.getImages().stream()
                    .filter(img -> img.getPosition() != null && img.getPosition() == 1)
                    .findFirst()
                    .orElse(null);
            if (cover != null && cover.getUrl() != null) {
                article.setCoverImage(cover.getUrl());
            }
        }
        article.setImages(GsonUtils.toJson(state.getImages()));
        article.setCompletedTime(LocalDateTime.now());
        this.updateById(article);
        log.info("文章保存成功, taskId={}", taskId);
    }

    /**
     * 分页查询文章列表
     *
     * @param request 查询参数
     * @param loginUser 登录用户
     * @return 文章列表
     */
    @Override
    public Page<ArticleVO> listArticleByPage(ArticleQueryRequest request, User loginUser) {
        long current = request.getCurrent();
        long size = request.getPageSize();
        // 构建查询条件
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("isDelete", 0)
                .orderBy("createTime", false);
        // 非管理员只能查看自己的文章
        if (!ADMIN_ROLE.equals(loginUser.getUserRole())) {
            queryWrapper.eq("userId", loginUser.getId());
        } else if (request.getUserId() != null) {
            queryWrapper.eq("userId", request.getUserId());
        }
        // 按状态筛选
        if (request.getStatus() != null && !request.getStatus().trim().isEmpty()) {
            queryWrapper.eq("status", request.getStatus());
        }
        // 分页查询
        Page<Article> articlePage = this.page(new Page<>(current, size), queryWrapper);
        // 转换为 VO
        Page<ArticleVO> voPage = new Page<>(articlePage.getPageNumber(), articlePage.getPageSize(), articlePage.getTotalRow());
        List<ArticleVO> voList = articlePage.getRecords().stream()
                .map(ArticleVO::objToVO)
                .collect(Collectors.toList());
        voPage.setRecords(voList);
        return voPage;
    }

    /**
     * 删除文章
     *
     * @param id 文章ID
     * @param loginUser 登录用户
     * @return 是否删除成功
     */
    @Override
    public boolean deleteArticle(Long id, User loginUser) {
        Article article = this.getById(id);
        ThrowUtils.throwIf(article == null, HttpsCodeEnum.NOT_FOUND_ERROR);
        // 校验权限：只能删除自己的文章（管理员除外）
        checkArticlePermission(article, loginUser);
        // 逻辑删除
        return this.removeById(id);
    }

    /**
     * 确认标题
     *
     * @param taskId 任务ID
     * @param mainTitle 主标题
     * @param subTitle 副标题
     * @param userDescription 用户描述
     * @param loginUser 登录用户
     */
    @Override
    public void confirmTitle(String taskId, String mainTitle, String subTitle, String userDescription, User loginUser) {
        Article article = getByTaskId(taskId);
        ThrowUtils.throwIf(article == null, HttpsCodeEnum.NOT_FOUND_ERROR, "文章不存在");

        // 校验权限
        checkArticlePermission(article, loginUser);

        // 校验当前阶段（必须是 TITLE_SELECTING）
        ArticlePhaseEnum currentPhase = ArticlePhaseEnum.getByValue(article.getPhase());
        ThrowUtils.throwIf(currentPhase != ArticlePhaseEnum.TITLE_SELECTING, HttpsCodeEnum.OPERATION_ERROR, "当前阶段不允许此操作");

        // 保存用户选择的标题和补充描述
        article.setMainTitle(mainTitle);
        article.setSubTitle(subTitle);
        article.setUserDescription(userDescription);
        article.setPhase(ArticlePhaseEnum.OUTLINE_GENERATING.getValue());
        this.updateById(article);
        log.info("用户确认标题, taskId={}, mainTitle={}", taskId, mainTitle);
    }

    /**
     * 确认大纲
     *
     * @param taskId 任务ID
     * @param outline 大纲
     * @param loginUser 登录用户
     */
    @Override
    public void confirmOutline(String taskId, List<ArticleState.OutlineSection> outline, User loginUser) {
        Article article = getByTaskId(taskId);
        ThrowUtils.throwIf(article == null, HttpsCodeEnum.NOT_FOUND_ERROR, "文章不存在");

        // 校验权限
        checkArticlePermission(article, loginUser);

        // 校验当前阶段（必须是 OUTLINE_EDITING）
        ArticlePhaseEnum currentPhase = ArticlePhaseEnum.getByValue(article.getPhase());
        ThrowUtils.throwIf(currentPhase != ArticlePhaseEnum.OUTLINE_EDITING, HttpsCodeEnum.OPERATION_ERROR, "当前阶段不允许此操作");

        // 保存用户编辑后的大纲
        article.setOutline(GsonUtils.toJson(outline));
        article.setPhase(ArticlePhaseEnum.CONTENT_GENERATING.getValue());
        this.updateById(article);
        log.info("用户确认大纲, taskId={}, sectionsCount={}", taskId, outline.size());
    }

    /**
     * 更新文章阶段
     *
     * @param taskId 任务ID
     * @param phase 新阶段
     */
    @Override
    public void updatePhase(String taskId, ArticlePhaseEnum phase) {
        Article article = getByTaskId(taskId);
        if (article == null) {
            log.error("文章记录不存在, taskId={}", taskId);
            return;
        }
        article.setPhase(phase.getValue());
        this.updateById(article);
        log.info("文章阶段已更新, taskId={}, phase={}", taskId, phase.getValue());
    }

    /**
     * 保存标题方案
     *
     * @param taskId 任务ID
     * @param titleOptions 标题方案
     */
    @Override
    public void saveTitleOptions(String taskId, List<ArticleState.TitleOption> titleOptions) {
        Article article = getByTaskId(taskId);
        if (article == null) {
            log.error("文章记录不存在, taskId={}", taskId);
            return;
        }
        article.setTitleOptions(GsonUtils.toJson(titleOptions));
        this.updateById(article);
        log.info("标题方案已保存, taskId={}, optionsCount={}", taskId, titleOptions.size());
    }

    /**
     * AI 修改大纲
     *
     * @param taskId 任务ID
     * @param modifySuggestion 修改建议
     * @param loginUser 登录用户
     * @return 修改后的大纲
     */
    @Override
    public List<ArticleState.OutlineSection> aiModifyOutline(String taskId, String modifySuggestion, User loginUser) {
        Article article = getByTaskId(taskId);
        ThrowUtils.throwIf(article == null, HttpsCodeEnum.NOT_FOUND_ERROR, "文章不存在");

        // 校验权限
        checkArticlePermission(article, loginUser);

        // 校验当前阶段（必须是 OUTLINE_EDITING）
        ArticlePhaseEnum currentPhase = ArticlePhaseEnum.getByValue(article.getPhase());
        ThrowUtils.throwIf(currentPhase != ArticlePhaseEnum.OUTLINE_EDITING, HttpsCodeEnum.OPERATION_ERROR, "当前阶段不允许此操作");

        // 获取当前大纲
        List<ArticleState.OutlineSection> currentOutline = GsonUtils.fromJson(
                article.getOutline(),
                new TypeToken<List<ArticleState.OutlineSection>>(){}
        );

        // 调用 AI 修改大纲
        List<ArticleState.OutlineSection> modifiedOutline = articleAgentService.aiModifyOutline(
                article.getMainTitle(),
                article.getSubTitle(),
                currentOutline,
                modifySuggestion
        );

        // 保存修改后的大纲
        article.setOutline(GsonUtils.toJson(modifiedOutline));
        this.updateById(article);

        log.info("AI修改大纲完成, taskId={}, sectionsCount={}", taskId, modifiedOutline.size());
        return modifiedOutline;
    }

    /**
     * 校验权限：只能删除自己的文章（管理员除外）
     *
     * @param article 文章
     * @param loginUser 登录用户
     */
    public void checkArticlePermission(Article article, User loginUser) {
        if (!article.getUserId().equals(loginUser.getId()) && !ADMIN_ROLE.equals(loginUser.getUserRole())) {
            throw new BusinessException(HttpsCodeEnum.UNAUTHORIZED, "无权限操作此文章");
        }
    }
}
