package com.lilac.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.lilac.annotation.AuthCheck;
import com.lilac.common.DeleteRequest;
import com.lilac.constant.UserConstant;
import com.lilac.domain.dto.user.*;
import com.lilac.domain.entity.User;
import com.lilac.domain.result.Result;
import com.lilac.domain.vo.LoginUserVO;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.service.UserService;
import com.lilac.utils.ThrowUtils;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户接口
 */
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public Result<Long> userRegister(@RequestBody UserRegisterRequest userRegisterRequest) {
        ThrowUtils.throwIf(userRegisterRequest == null, HttpsCodeEnum.PARAMS_ERROR);
        String userAccount = userRegisterRequest.getUserAccount();
        String userPassword = userRegisterRequest.getUserPassword();
        String checkPassword = userRegisterRequest.getCheckPassword();
        long result = userService.userRegister(userAccount, userPassword, checkPassword);
        return Result.success(result);
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public Result<LoginUserVO> userLogin(@RequestBody UserLoginRequest userLoginRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(userLoginRequest == null, HttpsCodeEnum.PARAMS_ERROR);
        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();
        LoginUserVO loginUserVO = userService.userLogin(userAccount, userPassword, request);
        return Result.success(loginUserVO);
    }

    /**
     * 获取当前登录用户
     */
    @GetMapping("/get/login")
    public Result<LoginUserVO> getLoginUser(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return Result.success(userService.getLoginUserVO(loginUser));
    }

    /**
     * 用户注销
     */
    @PostMapping("/logout")
    public Result<Boolean> userLogout(HttpServletRequest request) {
        ThrowUtils.throwIf(request == null, HttpsCodeEnum.PARAMS_ERROR);
        boolean result = userService.userLogout(request);
        return Result.success(result);
    }

    /**
     * 创建用户（管理员）
     */
    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public Result<Long> addUser(@RequestBody UserAddRequest userAddRequest) {
        ThrowUtils.throwIf(userAddRequest == null, HttpsCodeEnum.PARAMS_ERROR);
        User user = new User();
        BeanUtil.copyProperties(userAddRequest, user);
        String defaultPassword = "123456";
        user.setUserPassword(userService.getEncryptPassword(defaultPassword));
        boolean result = userService.save(user);
        ThrowUtils.throwIf(!result, HttpsCodeEnum.OPERATION_ERROR);
        return Result.success(user.getId());
    }

    /**
     * 删除用户（管理员）
     */
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public Result<Boolean> deleteUser(@RequestBody DeleteRequest deleteRequest) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() == null || deleteRequest.getId() <= 0,
                HttpsCodeEnum.PARAMS_ERROR);
        boolean result = userService.removeById(deleteRequest.getId());
        ThrowUtils.throwIf(!result, HttpsCodeEnum.OPERATION_ERROR);
        return Result.success(true);
    }

    /**
     * 分页查询用户列表（仅管理员）
     *
     * @param queryRequest 查询请求
     * @return 用户分页
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public Result<Page<LoginUserVO>> listUserVoByPage(@RequestBody UserQueryRequest queryRequest) {
        ThrowUtils.throwIf(queryRequest == null, HttpsCodeEnum.PARAMS_ERROR);
        QueryWrapper queryWrapper = QueryWrapper.create();
        if (queryRequest.getId() != null) {
            queryWrapper.eq("id", queryRequest.getId());
        }
        if (StrUtil.isNotBlank(queryRequest.getUserAccount())) {
            queryWrapper.like("userAccount", queryRequest.getUserAccount());
        }
        if (StrUtil.isNotBlank(queryRequest.getUserName())) {
            queryWrapper.like("userName", queryRequest.getUserName());
        }
        if (StrUtil.isNotBlank(queryRequest.getUserProfile())) {
            queryWrapper.like("userProfile", queryRequest.getUserProfile());
        }
        queryWrapper.orderBy("createTime", false);

        Page<User> userPage = userService.page(Page.of(queryRequest.getCurrent(), queryRequest.getPageSize()), queryWrapper);
        Page<LoginUserVO> voPage = new Page<>(userPage.getPageNumber(), userPage.getPageSize(), userPage.getTotalRow());
        List<LoginUserVO> voList = userPage.getRecords().stream()
                .map(userService::getLoginUserVO)
                .collect(Collectors.toList());
        voPage.setRecords(voList);
        return Result.success(voPage);
    }

    /**
     * 更新用户（仅管理员）
     *
     * @param updateRequest 更新请求
     * @return 是否成功
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public Result<Boolean> updateUser(@RequestBody UserUpdateRequest updateRequest) {
        ThrowUtils.throwIf(updateRequest == null || updateRequest.getId() == null, HttpsCodeEnum.PARAMS_ERROR);
        User user = userService.getById(updateRequest.getId());
        ThrowUtils.throwIf(user == null, HttpsCodeEnum.NOT_FOUND_ERROR, "用户不存在");
        user.setUserName(updateRequest.getUserName());
        user.setUserAvatar(updateRequest.getUserAvatar());
        user.setUserProfile(updateRequest.getUserProfile());
        if (StrUtil.isNotBlank(updateRequest.getUserRole())) {
            user.setUserRole(updateRequest.getUserRole());
        }
        boolean updated = userService.updateById(user);
        return Result.success(updated);
    }
}
