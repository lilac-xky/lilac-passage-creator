package com.lilac.controller;

import com.lilac.service.PaymentService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Stripe Webhook 控制器
 *
 * <p><b>为什么状态码很关键（步骤 C5）</b>
 *
 * <p>Stripe 的投递策略是：<b>收到非 2xx 就重试</b>，且重试带指数退避，
 * 持续 <b>最长 3 天</b>（官方文档的默认行为）。
 *
 * <p>改造前的实现把所有异常都吞成 500：
 * <pre>
 * catch (Exception e) {
 *     return ResponseEntity.internalServerError().body("error");
 * }
 * </pre>
 * 这会导致两类本不该重试的情况被无限重试：
 *
 * <ol>
 *   <li><b>签名校验失败</b>：这是攻击/配置错误，重试一万次也不会成功。
 *       而且会持续消耗服务器资源，日志被刷爆。</li>
 *   <li><b>事件体解析失败 / 数据结构不符</b>：同样是确定性失败，
 *       重试无意义。</li>
 * </ol>
 *
 * <p>正确做法是<b>按异常性质分类</b>：
 * <ul>
 *   <li>可恢复（数据库超时、下游 5xx）→ 5xx，让 Stripe 重试</li>
 *   <li>不可恢复（签名错、结构错）→ 2xx，明确告诉 Stripe "别发了"</li>
 * </ul>
 *
 * <p>注意：对不可恢复事件返回 2xx 意味着<b>主动放弃</b>这个事件，
 * 所以必须打 ERROR 日志并带上 eventId，方便人工补偿。
 */
@RestController
@RequestMapping("/webhook")
@Slf4j
@Hidden
public class StripeWebhookController {

    @Resource
    private PaymentService paymentService;

    /**
     * 处理 Stripe Webhook 回调
     */
    @PostMapping("/stripe")
    public ResponseEntity<String> handleStripeWebhook(@RequestBody String payload,
                                                      @RequestHeader("Stripe-Signature") String sigHeader) {

        // ---------- 第一类：签名校验失败 ----------
        // 攻击者伪造请求，或 webhook-secret 配置错误。
        // 重试永远不可能成功，返回 2xx 让 Stripe 停止投递。
        Event event;
        try {
            event = paymentService.constructEvent(payload, sigHeader);
        } catch (SignatureVerificationException e) {
            log.error("Stripe Webhook 签名校验失败，已放弃该请求（不会重试）。"
                    + "请检查 stripe.webhook-secret 配置是否正确", e);
            return ResponseEntity.ok("ignored: invalid signature");
        } catch (Exception e) {
            // payload 结构异常等，同样属于确定性失败
            log.error("Stripe Webhook 事件构造失败，已放弃（不会重试）", e);
            return ResponseEntity.ok("ignored: malformed event");
        }

        log.info("收到 Stripe Webhook 事件, type={}, eventId={}", event.getType(), event.getId());

        // ---------- 第二类：事件处理 ----------
        try {
            switch (event.getType()) {
                case "checkout.session.completed":
                    paymentService.handlePaymentSuccess(deserializeSession(event));
                    break;
                case "checkout.session.async_payment_succeeded":
                    paymentService.handlePaymentSuccess(deserializeSession(event));
                    break;
                default:
                    // 未订阅的事件类型：正常情况，直接确认收到
                    log.info("未处理的事件类型: {}", event.getType());
                    break;
            }
            return ResponseEntity.ok("success");

        } catch (NonRetryableEventException e) {
            // 数据结构不符等，重试无意义
            log.error("Stripe 事件结构异常，已放弃。eventId={}, type={}",
                    event.getId(), event.getType(), e);
            return ResponseEntity.ok("ignored: " + e.getMessage());

        } catch (Exception e) {
            // 数据库超时、下游不可用等瞬时故障：返回 5xx 让 Stripe 重试。
            // 注意 handlePaymentSuccess 内部有幂等检查，
            // 重复投递不会重复升级 VIP。
            log.error("处理 Stripe Webhook 出现可重试错误，将等待 Stripe 重新投递。eventId={}, type={}",
                    event.getId(), event.getType(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("retryable error");
        }
    }

    /**
     * 反序列化事件数据为 Checkout Session。
     *
     * @throws NonRetryableEventException 数据结构不符（重试无意义）
     */
    private Session deserializeSession(Event event) {
        StripeObject stripeObject = event.getDataObjectDeserializer().getObject().orElse(null);
        if (stripeObject == null) {
            log.warn("Stripe Webhook API 版本与 SDK 模型不匹配，使用兼容反序列化, eventId={}",
                    event.getId());
            try {
                stripeObject = event.getDataObjectDeserializer().deserializeUnsafe();
            } catch (Exception e) {
                throw new NonRetryableEventException("事件数据无法反序列化", e);
            }
        }
        if (!(stripeObject instanceof Session session)) {
            throw new NonRetryableEventException(
                    "期望 Checkout Session，实际为 " + (stripeObject == null ? "null" : stripeObject.getClass()), null);
        }
        return session;
    }

    /**
     * 标记「重试也不会成功」的事件处理异常。
     */
    private static class NonRetryableEventException extends RuntimeException {
        NonRetryableEventException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
