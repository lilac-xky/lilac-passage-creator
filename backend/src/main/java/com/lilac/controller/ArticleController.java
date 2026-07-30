package com.lilac.controller;

import com.lilac.annotation.AuthCheck;
import com.lilac.common.DeleteRequest;
import com.lilac.domain.dto.article.*;
import com.lilac.domain.entity.User;
import com.lilac.domain.result.Result;
import com.lilac.domain.vo.ArticleVO;
import com.lilac.enums.ArticleStyleEnum;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.manager.SseEmitterManager;
import com.lilac.service.ArticleService;
import com.lilac.service.UserService;
import com.lilac.service.ArticleAsyncService;
import com.lilac.utils.ThrowUtils;
import com.mybatisflex.core.paginate.Page;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 文章接口
 */
@RestController
@RequestMapping("/article")
@Slf4j
public class ArticleController {

    @Resource
    private ArticleService articleService;
    @Resource
    private ArticleAsyncService articleAsyncService;
    @Resource
    private SseEmitterManager sseEmitterManager;
    @Resource
    private UserService userService;

    /**
     * 创建文章任务
     */
    @PostMapping("/create")
    public Result<String> createArticle(@RequestBody ArticleCreateRequest request, HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(request == null, HttpsCodeEnum.PARAMS_ERROR);
        ThrowUtils.throwIf(request.getTopic() == null || request.getTopic().trim().isEmpty(),
                HttpsCodeEnum.PARAMS_ERROR, "选题不能为空");
        // 校验风格参数（允许为空）
        ThrowUtils.throwIf(!ArticleStyleEnum.isValid(request.getStyle()), HttpsCodeEnum.PARAMS_ERROR, "无效的文章风格");

        User loginUser = userService.getLoginUser(httpServletRequest);
        // 检查并消耗配额 + 创建文章任务（在同一事务中）
        String taskId = articleService.createArticleTaskWithQuotaCheck(
                request.getTopic(),
                request.getStyle(),
                request.getEnabledImageMethods(),
                loginUser
        );

        // 异步执行阶段1：生成标题方案
        articleAsyncService.executePhase1(taskId, request.getTopic(), request.getStyle());
        return Result.success(taskId);
    }

    /**
     * 确认标题并输入补充描述
     */
    @PostMapping("/confirm-title")
    public Result<Void> confirmTitle(@RequestBody ArticleConfirmTitleRequest request, HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(request == null, HttpsCodeEnum.PARAMS_ERROR);
        ThrowUtils.throwIf(request.getTaskId() == null || request.getTaskId().trim().isEmpty(),
                HttpsCodeEnum.PARAMS_ERROR, "任务ID不能为空");
        ThrowUtils.throwIf(request.getSelectedMainTitle() == null || request.getSelectedMainTitle().trim().isEmpty(),
                HttpsCodeEnum.PARAMS_ERROR, "主标题不能为空");
        ThrowUtils.throwIf(request.getSelectedSubTitle() == null || request.getSelectedSubTitle().trim().isEmpty(),
                HttpsCodeEnum.PARAMS_ERROR, "副标题不能为空");

        User loginUser = userService.getLoginUser(httpServletRequest);
        // 确认标题
        articleService.confirmTitle(request.getTaskId(),
                request.getSelectedMainTitle(),
                request.getSelectedSubTitle(),
                request.getUserDescription(),
                loginUser
        );

        // 异步执行阶段2：生成大纲
        articleAsyncService.executePhase2(request.getTaskId());
        return Result.success(null);
    }

    /**
     * 确认大纲
     */
    @PostMapping("/confirm-outline")
    public Result<Void> confirmOutline(@RequestBody ArticleConfirmOutlineRequest request, HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(request == null, HttpsCodeEnum.PARAMS_ERROR);
        ThrowUtils.throwIf(request.getTaskId() == null || request.getTaskId().trim().isEmpty(),
                HttpsCodeEnum.PARAMS_ERROR, "任务ID不能为空");
        ThrowUtils.throwIf(request.getOutline() == null || request.getOutline().isEmpty(),
                HttpsCodeEnum.PARAMS_ERROR, "大纲不能为空");

        User loginUser = userService.getLoginUser(httpServletRequest);
        // 确认大纲
        articleService.confirmOutline(request.getTaskId(), request.getOutline(), loginUser);

        // 异步执行阶段3：生成正文+配图
        articleAsyncService.executePhase3(request.getTaskId());
        return Result.success(null);
    }

    /**
     * AI 修改大纲
     */
    @PostMapping("/ai-modify-outline")
    public Result<List<ArticleState.OutlineSection>> aiModifyOutline(@RequestBody ArticleAiModifyOutlineRequest request,
                                                                     HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(request == null, HttpsCodeEnum.PARAMS_ERROR);
        ThrowUtils.throwIf(request.getTaskId() == null || request.getTaskId().trim().isEmpty(),
                HttpsCodeEnum.PARAMS_ERROR, "任务ID不能为空");
        ThrowUtils.throwIf(request.getModifySuggestion() == null || request.getModifySuggestion().trim().isEmpty(),
                HttpsCodeEnum.PARAMS_ERROR, "修改建议不能为空");

        User loginUser = userService.getLoginUser(httpServletRequest);
        // AI 修改大纲
        List<ArticleState.OutlineSection> modifiedOutline = articleService.aiModifyOutline(
                request.getTaskId(),
                request.getModifySuggestion(),
                loginUser
        );
        return Result.success(modifiedOutline);
    }

    /**
     * SSE 进度推送
     */
    @GetMapping("/progress/{taskId}")
    public SseEmitter getProgress(@PathVariable String taskId, HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(taskId == null || taskId.trim().isEmpty(), HttpsCodeEnum.PARAMS_ERROR, "任务ID不能为空");
        // 校验权限（内部会检查任务是否存在以及用户是否有权限访问）
        User loginUser = userService.getLoginUser(httpServletRequest);
        articleService.getArticleDetail(taskId, loginUser);
        // 创建 SSE Emitter
        SseEmitter emitter = sseEmitterManager.createEmitter(taskId);
        log.info("SSE 连接已建立, taskId={}", taskId);
        return emitter;
    }

    /**
     * 获取文章详情
     */
    @GetMapping("/{taskId}")
    @AuthCheck(mustRole = "user")
    public Result<ArticleVO> getArticle(@PathVariable String taskId, HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(taskId == null || taskId.trim().isEmpty(), HttpsCodeEnum.PARAMS_ERROR, "任务ID不能为空");
        User loginUser = userService.getLoginUser(httpServletRequest);
        ArticleVO articleVO = articleService.getArticleDetail(taskId, loginUser);
        return Result.success(articleVO);
    }

    /**
     * 分页查询文章列表
     */
    @PostMapping("/list")
    @AuthCheck(mustRole = "user")
    public Result<Page<ArticleVO>> listArticle(@RequestBody ArticleQueryRequest request, HttpServletRequest httpServletRequest) {
        User loginUser = userService.getLoginUser(httpServletRequest);
        Page<ArticleVO> articleVOPage = articleService.listArticleByPage(request, loginUser);
        return Result.success(articleVOPage);
    }

    /**
     * 删除文章
     */
    @PostMapping("/delete")
    @AuthCheck(mustRole = "user")
    public Result<Boolean> deleteArticle(@RequestBody DeleteRequest deleteRequest, HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() == null, HttpsCodeEnum.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpServletRequest);
        boolean result = articleService.deleteArticle(deleteRequest.getId(), loginUser);
        return Result.success(result);
    }
}
