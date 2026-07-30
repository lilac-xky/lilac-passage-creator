package com.lilac.service.impl;

import com.lilac.config.StripeConfig;
import com.lilac.constant.UserConstant;
import com.lilac.domain.entity.PaymentRecord;
import com.lilac.domain.entity.User;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.enums.PaymentStatusEnum;
import com.lilac.enums.ProductTypeEnum;
import com.lilac.exception.BusinessException;
import com.lilac.mapper.PaymentRecordMapper;
import com.lilac.mapper.UserMapper;
import com.lilac.service.PaymentService;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.Refund;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.param.RefundCreateParams;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import com.mybatisflex.core.query.QueryWrapper;

/**
 * 支付服务实现
 */
@Service
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private static final String CURRENCY_USD = "usd";
    private static final long CENTS_MULTIPLIER = 100L;

    @Resource
    private StripeConfig stripeConfig;
    @Resource
    private UserMapper userMapper;
    @Resource
    private PaymentRecordMapper paymentRecordMapper;

    /**
     * 创建 VIP 永久会员支付会话
     *
     * @param userId 用户ID
     * @return Stripe Checkout Session URL
     */
    @Override
    public String createVipPaymentSession(Long userId) throws StripeException {
        User user = getUserOrThrow(userId);
        validateNotVip(user);

        ProductTypeEnum productType = ProductTypeEnum.VIP_PERMANENT;
        Session session = createStripeSession(userId, productType);
        savePaymentRecord(userId, session, productType);

        log.info("创建支付会话成功, userId={}, sessionId={}", userId, session.getId());
        return session.getUrl();
    }

    /**
     * 创建 Stripe 支付会话
     */
    private Session createStripeSession(Long userId, ProductTypeEnum productType) throws StripeException {
        long amountInCents = productType.getPrice().multiply(new BigDecimal(CENTS_MULTIPLIER)).longValue();

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(stripeConfig.getSuccessUrl())
                .setCancelUrl(stripeConfig.getCancelUrl())
                .addLineItem(buildLineItem(productType, amountInCents))
                .putMetadata("userId", String.valueOf(userId))
                .putMetadata("productType", productType.getValue())
                .build();
        return Session.create(params);
    }

    /**
     * 构建支付行项目
     */
    private SessionCreateParams.LineItem buildLineItem(ProductTypeEnum productType, long amountInCents) {
        return SessionCreateParams.LineItem.builder()
                .setPriceData(
                        SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency(CURRENCY_USD)
                                .setUnitAmount(amountInCents)
                                .setProductData(
                                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                .setName(productType.getDescription())
                                                .setDescription("解锁全部高级功能，无限创作配额，终身有效")
                                                .build()
                                )
                                .build()
                )
                .setQuantity(1L)
                .build();
    }

    /**
     * 保存支付记录
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handlePaymentSuccess(Session session) {
        String sessionId = session.getId();
        String userId = session.getMetadata().get("userId");
        String paymentIntentId = session.getPaymentIntent();

        PaymentRecord record = findPaymentRecordBySessionId(sessionId);
        if (record == null) {
            log.warn("支付记录不存在, sessionId={}", sessionId);
            return;
        }

        // 幂等性检查
        if (PaymentStatusEnum.SUCCEEDED.getValue().equals(record.getStatus())) {
            log.info("支付记录已处理, sessionId={}", sessionId);
            return;
        }

        updatePaymentStatus(record.getId(), PaymentStatusEnum.SUCCEEDED, paymentIntentId);
        upgradeUserToVip(Long.valueOf(userId));
        log.info("支付成功，用户已升级为 VIP, userId={}, sessionId={}", userId, sessionId);
    }

    /**
     * 升级用户为 VIP
     */
    private void upgradeUserToVip(Long userId) {
        User user = new User();
        user.setId(userId);
        user.setVipTime(LocalDateTime.now());
        user.setUserRole(UserConstant.VIP_ROLE);
        userMapper.update(user);
    }

    /**
     * 处理退款
     *
     * @param userId 用户ID
     * @param reason 退款原因
     * @return 是否退款成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean handleRefund(Long userId, String reason) throws StripeException {
        User user = getUserOrThrow(userId);
        validateIsVip(user);

        PaymentRecord paymentRecord = findLatestSuccessfulPayment(userId);
        if (paymentRecord == null) {
            throw new BusinessException(HttpsCodeEnum.NOT_FOUND_ERROR, "未找到支付记录");
        }

        if (paymentRecord.getStripePaymentIntentId() == null) {
            throw new BusinessException(HttpsCodeEnum.OPERATION_ERROR, "支付记录无效");
        }

        Refund refund = createStripeRefund(paymentRecord.getStripePaymentIntentId());
        if (!"succeeded".equals(refund.getStatus())) {
            return false;
        }

        updateRefundRecord(paymentRecord.getId(), reason);
        revokeVipStatus(userId);

        log.info("退款成功，已取消 VIP 身份, userId={}, refundId={}", userId, refund.getId());
        return true;
    }

    /**
     * 撤销用户 VIP 身份
     */
    private void revokeVipStatus(Long userId) {
        User updateUser = new User();
        updateUser.setId(userId);
        updateUser.setVipTime(null);
        updateUser.setUserRole(UserConstant.DEFAULT_ROLE);
        updateUser.setQuota(UserConstant.DEFAULT_QUOTA);
        userMapper.update(updateUser);
    }

    /**
     * 验证 Webhook 签名
     *
     * @param payload 请求体
     * @param sigHeader 签名头
     * @return Stripe Event
     */
    @Override
    public Event constructEvent(String payload, String sigHeader) throws Exception {
        return Webhook.constructEvent(payload, sigHeader, stripeConfig.getWebhookSecret());
    }

    /**
     * 获取用户支付记录
     *
     * @param userId 用户ID
     * @return 支付记录列表
     */
    @Override
    public List<PaymentRecord> getPaymentRecords(Long userId) {
        QueryWrapper query = QueryWrapper.create().eq("userId", userId).orderBy("createTime", false);
        return paymentRecordMapper.selectListByQuery(query);
    }

    /**
     * 获取用户
     */
    private User getUserOrThrow(Long userId) {
        User user = userMapper.selectOneById(userId);
        if (user == null) throw new BusinessException(HttpsCodeEnum.NOT_FOUND_ERROR, "用户不存在");
        return user;
    }

    /**
     * 验证用户是否为 VIP
     */
    private void validateNotVip(User user) {
        if (UserConstant.VIP_ROLE.equals(user.getUserRole())) throw new BusinessException(HttpsCodeEnum.OPERATION_ERROR, "已经是 VIP 用户");
    }

    /**
     * 验证用户是否为 VIP
     */
    private void validateIsVip(User user) {
        if (!UserConstant.VIP_ROLE.equals(user.getUserRole())) throw new BusinessException(HttpsCodeEnum.OPERATION_ERROR, "当前不是 VIP 用户");
    }

    /**
     * 保存支付记录
     */
    private void savePaymentRecord(Long userId, Session session, ProductTypeEnum productType) {
        PaymentRecord record = PaymentRecord.builder().userId(userId).stripeSessionId(session.getId())
                .amount(productType.getPrice()).currency(CURRENCY_USD).status(PaymentStatusEnum.PENDING.getValue())
                .productType(productType.getValue()).description(productType.getDescription()).build();
        paymentRecordMapper.insert(record);
    }

    /**
     * 更新退款记录
     */
    private PaymentRecord findPaymentRecordBySessionId(String sessionId) {
        return paymentRecordMapper.selectOneByQuery(QueryWrapper.create().eq("stripeSessionId", sessionId));
    }

    /**
     * 获取用户最新成功的支付记录
     */
    private PaymentRecord findLatestSuccessfulPayment(Long userId) {
        return paymentRecordMapper.selectOneByQuery(QueryWrapper.create().eq("userId", userId)
                .eq("status", PaymentStatusEnum.SUCCEEDED.getValue()).orderBy("createTime", false).limit(1));
    }

    /**
     * 更新支付记录状态
     */
    private void updatePaymentStatus(Long id, PaymentStatusEnum status, String paymentIntentId) {
        PaymentRecord update = new PaymentRecord(); update.setId(id); update.setStatus(status.getValue()); update.setStripePaymentIntentId(paymentIntentId); paymentRecordMapper.update(update);
    }

    /**
     * 创建 Stripe 退款
     */
    private Refund createStripeRefund(String paymentIntentId) throws StripeException {
        return Refund.create(RefundCreateParams.builder().setPaymentIntent(paymentIntentId).build());
    }

    /**
     * 更新退款记录
     */
    private void updateRefundRecord(Long id, String reason) {
        PaymentRecord update = new PaymentRecord(); update.setId(id); update.setStatus("REFUNDED"); update.setRefundReason(reason); update.setRefundTime(LocalDateTime.now()); paymentRecordMapper.update(update);
    }
}
