package com.lilac.controller;

import com.lilac.annotation.AuthCheck;
import com.lilac.common.DeleteRequest;
import com.lilac.domain.dto.article.ArticleCreateRequest;
import com.lilac.domain.dto.article.ArticleQueryRequest;
import com.lilac.domain.entity.User;
import com.lilac.domain.result.Result;
import com.lilac.domain.vo.ArticleVO;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.manager.SseEmitterManager;
import com.lilac.service.ArticleService;
import com.lilac.service.UserService;
import com.lilac.service.impl.ArticleAsyncService;
import com.lilac.utils.ThrowUtils;
import com.mybatisflex.core.paginate.Page;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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

        User loginUser = userService.getLoginUser(httpServletRequest);
        // 创建文章任务
        String taskId = articleService.createArticleTask(request.getTopic(), loginUser);
        // 在异步任务启动前注册 Emitter，避免前端建立 SSE 连接前产生的事件丢失
        sseEmitterManager.createEmitter(taskId);
        // 异步执行文章生成
        articleAsyncService.executeArticleGeneration(taskId, request.getTopic());
        return Result.success(taskId);
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
