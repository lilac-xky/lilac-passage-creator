package com.lilac.aop;

import com.lilac.annotation.AuthCheck;
import com.lilac.domain.entity.User;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.enums.UserRoleEnum;
import com.lilac.exception.BusinessException;
import com.lilac.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 权限校验 AOP
 */
@Aspect
@Component
public class AuthInterceptor {

    @Resource
    private UserService userService;

    @Around("@annotation(authCheck)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        String mustRole = authCheck.mustRole();
        RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
        // 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        UserRoleEnum mustRoleEnum = UserRoleEnum.getEnumByValue(mustRole);
        // 不需要权限，直接放行
        if (mustRoleEnum == null) {
            return joinPoint.proceed();
        }
        // 必须有这个权限才能通过
        UserRoleEnum userRoleEnum = UserRoleEnum.getEnumByValue(loginUser.getUserRole());
        if (userRoleEnum == null) {
            throw new BusinessException(HttpsCodeEnum.UNAUTHORIZED);
        }
        // user 表示已登录；VIP 权限同时向管理员开放；管理员接口仅允许管理员。
        boolean hasRequiredRole = switch (mustRoleEnum) {
            case USER -> true;
            case VIP -> UserRoleEnum.VIP.equals(userRoleEnum) || UserRoleEnum.ADMIN.equals(userRoleEnum);
            case ADMIN -> UserRoleEnum.ADMIN.equals(userRoleEnum);
        };
        if (!hasRequiredRole) {
            throw new BusinessException(HttpsCodeEnum.UNAUTHORIZED);
        }
        // 通过权限校验，放行
        return joinPoint.proceed();
    }
}
