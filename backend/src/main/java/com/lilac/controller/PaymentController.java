package com.lilac.controller;

import com.lilac.annotation.AuthCheck;
import com.lilac.constant.UserConstant;
import com.lilac.domain.entity.PaymentRecord;
import com.lilac.domain.entity.User;
import com.lilac.domain.result.Result;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.exception.BusinessException;
import com.lilac.service.PaymentService;
import com.lilac.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 支付控制器
 */
@RestController
@RequestMapping("/payment")
@Slf4j
public class PaymentController {

    @Resource
    private PaymentService paymentService;
    @Resource
    private UserService userService;

    /**
     * 创建 VIP 支付会话
     */
    @PostMapping("/create-vip-session")
    public Result<String> createVipPaymentSession(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        try {
            String sessionUrl = paymentService.createVipPaymentSession(loginUser.getId());
            return Result.success(sessionUrl);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("创建支付会话失败", e);
            throw new BusinessException(HttpsCodeEnum.SYSTEM_ERROR, "创建支付会话失败");
        }
    }

    /**
     * 申请退款
     */
    @PostMapping("/refund")
    @AuthCheck(mustRole = UserConstant.VIP_ROLE)
    public Result<Boolean> refund(@RequestParam(required = false) String reason, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        try {
            boolean success = paymentService.handleRefund(loginUser.getId(), reason);
            return Result.success(success);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("退款失败", e);
            throw new BusinessException(HttpsCodeEnum.SYSTEM_ERROR, "退款失败");
        }
    }

    /**
     * 获取当前用户支付记录
     */
    @GetMapping("/records")
    public Result<List<PaymentRecord>> getPaymentRecords(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        List<PaymentRecord> records = paymentService.getPaymentRecords(loginUser.getId());
        return Result.success(records);
    }
}