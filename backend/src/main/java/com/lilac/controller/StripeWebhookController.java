package com.lilac.controller;

import com.lilac.service.PaymentService;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Stripe Webhook 控制器
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
    public ResponseEntity<String> handleStripeWebhook(@RequestBody String payload, @RequestHeader("Stripe-Signature") String sigHeader) {
        try {
            // 验证 Webhook 签名
            Event event = paymentService.constructEvent(payload, sigHeader);
            log.info("收到 Stripe Webhook 事件, type={}", event.getType());

            // 处理事件
            switch (event.getType()) {
                case "checkout.session.completed":
                    // 支付成功
                    Session session = deserializeSession(event);
                    paymentService.handlePaymentSuccess(session);
                    break;
                case "checkout.session.async_payment_succeeded":
                    // 异步支付成功
                    Session asyncSession = deserializeSession(event);
                    paymentService.handlePaymentSuccess(asyncSession);
                    break;
                default:
                    log.info("未处理的事件类型: {}", event.getType());
                    break;
            }
            return ResponseEntity.ok("success");
        } catch (Exception e) {
            log.error("处理 Stripe Webhook 失败", e);
            // A non-2xx response tells Stripe to retry the event.
            return ResponseEntity.internalServerError().body("error");
        }
    }

    private Session deserializeSession(Event event) throws Exception {
        StripeObject stripeObject = event.getDataObjectDeserializer().getObject().orElse(null);
        if (stripeObject == null) {
            log.warn("Stripe Webhook API version does not match the SDK model; using compatibility deserialization, eventId={}", event.getId());
            stripeObject = event.getDataObjectDeserializer().deserializeUnsafe();
        }
        if (!(stripeObject instanceof Session session)) {
            throw new IllegalArgumentException("Stripe event data is not a Checkout Session");
        }
        return session;
    }
}
