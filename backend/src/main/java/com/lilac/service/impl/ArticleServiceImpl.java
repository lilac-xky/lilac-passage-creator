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
import com.lilac.enums.ImageMethodEnum;
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
import static com.lilac.constant.UserConstant.VIP_ROLE;

/**
 * 文章服务实现类
 */
@Service
@Slf4j
public class ArticleServiceImpl extends ServiceImpl<ArticleMapper, Article> implements ArticleService {

    /**
     * 配图降级率告警阈值（百分比）。
     *
     * <p>达到该比例即视为「严重降级」，前端应显式提示用户。
     * 实测历史数据里出现过 10 张图降级 5 张（50%）的情况，
     * 而 30% 这个阈值能把「个别图源偶发失败」与「图源整体不可用」区分开。
     */
    private static final int IMAGE_DEGRADE_WARN_PCT = 30;

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
        // 扣配额（内部已含原子校验：UPDATE ... WHERE quota > 0，影响行数为 0 则抛异常）。
        // 原代码此处还有一行 `quotaService.hasQuota(loginUser);` 但**丢弃了返回值**，
        // 属于无效调用：真正的前置检查在 checkAndConsumeQuota 内部已完成，
        // 那一行只白白多查一次数据库。已移除（步骤 C6）。
        //
        // 注意：配额扣减与任务创建在同一事务中，
        // 任务创建失败会整体回滚，不会出现"扣了配额但没有任务"。
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
        // 处理配图方式：如果用户未选择，给普通用户设置默认的非 VIP 方式
        List<String> finalImageMethods = processImageMethods(enabledImageMethods, loginUser);
        // 校验配图方式权限（普通用户不能使用 NANO_BANANA 和 SVG_DIAGRAM）
        validateImageMethods(finalImageMethods, loginUser);
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

        // Graph tasks may span a DevTools restart. Rebuild DTOs with the current
        // class loader before accessing list elements or persisting them.
        List<ArticleState.ImageResult> images = state.getImages() == null
                ? null
                : GsonUtils.fromJson(
                        GsonUtils.toJson(state.getImages()),
                        new TypeToken<List<ArticleState.ImageResult>>() {});
        state.setImages(images);

        // 保存封面图 URL（从 images 列表中提取 position=1 的 URL）
        if (images != null && !images.isEmpty()) {
            ArticleState.ImageResult cover = images.stream()
                    .filter(img -> img.getPosition() != null && img.getPosition() == 1)
                    .findFirst()
                    .orElse(null);
            if (cover != null && cover.getUrl() != null) {
                article.setCoverImage(cover.getUrl());
            }
        }
        article.setImages(GsonUtils.toJson(images));

        // 统计配图降级情况（步骤 E1）
        //
        // 背景：实测发现 34 张配图中 5 张（14.7%）静默降级到 PICSUM 占位图，
        // 而文章状态仍是「已完成」、errorMessage 为 NULL，用户在页面上
        // 完全看不出异常。这里把降级数量落库，让问题可被查询和展示。
        int imageTotal = images == null ? 0 : images.size();
        int fallbackCount = 0;
        if (images != null) {
            for (ArticleState.ImageResult img : images) {
                if (img != null && isFallbackMethod(img.getMethod())) {
                    fallbackCount++;
                }
            }
        }
        article.setImageTotal(imageTotal);
        article.setImageFallbackCount(fallbackCount);

        // 降级严重程度判定（步骤 E2）
        //
        // 为什么不直接把 status 改成 PARTIAL：
        // 前端有多处用 `status === '已完成'` 做分支判断
        // （ArticleCreate.vue:720 的会话恢复、ArticleList.vue:134 的标签映射等），
        // 新增状态值会让这些逻辑直接走错分支 —— 恢复会话时会把已完成的任务
        // 当成"仍在生成"。所以状态语义保持不变，改用独立字段表达降级程度，
        // 由前端按需展示提示，不影响既有流程。
        int fallbackRatePct = imageTotal == 0 ? 0 : (fallbackCount * 100 / imageTotal);
        article.setImageDegraded(fallbackCount > 0 && fallbackRatePct >= IMAGE_DEGRADE_WARN_PCT ? 1 : 0);

        if (fallbackCount > 0) {
            String level = fallbackRatePct >= IMAGE_DEGRADE_WARN_PCT ? "严重降级" : "轻微降级";
            log.warn("文章配图存在降级({}), taskId={}, 总数={}, 降级数={}, 降级率={}%",
                    level, taskId, imageTotal, fallbackCount, fallbackRatePct);
        }

        article.setCompletedTime(LocalDateTime.now());
        this.updateById(article);
        log.info("文章保存成功, taskId={}, 配图={}张(降级{}张)", taskId, imageTotal, fallbackCount);
    }

    /**
     * 判断图片生成方式是否为降级兜底（PICSUM）。
     *
     * <p>大小写不敏感：历史数据里同时存在 {@code PICSUM} 和 {@code Picsum} 两种写法。
     */
    private boolean isFallbackMethod(String method) {
        return method != null && ImageMethodEnum.getFallbackMethod().getValue()
                .equalsIgnoreCase(method);
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

        // 校验 VIP 权限（普通用户不能使用 AI 修改大纲）
        ThrowUtils.throwIf(!isVipOrAdmin(loginUser), HttpsCodeEnum.UNAUTHORIZED, "AI 修改大纲功能仅限 VIP 会员使用");

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

    /**
     * 处理配图方式
     * 如果用户未选择，给普通用户设置默认的非 VIP 方式，VIP 用户不限制
     */
    private List<String> processImageMethods(List<String> enabledImageMethods, User loginUser) {
        // 如果用户已选择，直接返回
        if (enabledImageMethods != null && !enabledImageMethods.isEmpty()) {
            return enabledImageMethods;
        }

        // VIP 和管理员：不限制，返回 null 表示支持所有方式
        if (isVipOrAdmin(loginUser)) {
            return null;
        }

        // 普通用户：返回默认的非 VIP 方式
        return List.of(
                ImageMethodEnum.PEXELS.getValue(),
                ImageMethodEnum.MERMAID.getValue(),
                ImageMethodEnum.ICONIFY.getValue(),
                ImageMethodEnum.EMOJI_PACK.getValue()
        );
    }

    /**
     * 校验配图方式权限
     * 普通用户不能使用 NANO_BANANA 和 SVG_DIAGRAM
     */
    private void validateImageMethods(List<String> enabledImageMethods, User loginUser) {
        if (enabledImageMethods == null || enabledImageMethods.isEmpty()) {
            return;
        }

        // VIP 和管理员无限制
        if (isVipOrAdmin(loginUser)) {
            return;
        }

        // 普通用户限制
        for (String method : enabledImageMethods) {
            if (ImageMethodEnum.NANO_BANANA.getValue().equals(method) || ImageMethodEnum.SVG_DIAGRAM.getValue().equals(method)) {
                throw new BusinessException(HttpsCodeEnum.UNAUTHORIZED, "高级配图功能（AI 生图、SVG 图表）仅限 VIP 会员使用");
            }
        }
    }

    /**
     * 判断是否为 VIP 或管理员
     */
    private boolean isVipOrAdmin(User user) {
        return ADMIN_ROLE.equals(user.getUserRole()) || VIP_ROLE.equals(user.getUserRole());
    }
}
