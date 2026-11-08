package com.lilac.unit;

import com.lilac.controller.StripeWebhookController;
import com.lilac.service.PaymentService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Stripe Webhook 状态码分类测试（步骤 C5）。
 *
 * <p>核心回归点：<b>不可恢复的错误必须返回 2xx</b>，
 * 否则 Stripe 会持续重试最长 3 天，把日志刷爆并浪费资源。
 *
 * <p>Stripe 的投递语义：收到非 2xx 就重试。
 * 所以「签名错」这种确定性失败返回 500 是错误设计。
 */
@DisplayName("Stripe Webhook 状态码分类")
class StripeWebhookControllerTest {

    private StripeWebhookController controller;
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        controller = new StripeWebhookController();
        paymentService = mock(PaymentService.class);
        ReflectionTestUtils.setField(controller, "paymentService", paymentService);
    }

    /** 构造一个带指定 type / eventId 的 Event，数据体为空 */
    private Event fakeEvent(String type, String eventId) {
        Event event = mock(Event.class);
        // 用 doReturn 而非 when(...).thenReturn(...)：
        // when() 的参数是 mock 方法调用，嵌套在其它 stubbing 里会触发
        // UnfinishedStubbingException。
        doReturn(type).when(event).getType();
        doReturn(eventId).when(event).getId();

        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        doReturn(Optional.empty()).when(deserializer).getObject();
        doReturn(deserializer).when(event).getDataObjectDeserializer();
        return event;
    }

    // ---------- 不可恢复：必须 2xx ----------

    @Test
    @DisplayName("【关键】签名校验失败返回 2xx，不让 Stripe 无限重试")
    void invalidSignatureReturns2xx() throws Exception {
        when(paymentService.constructEvent(anyString(), anyString()))
                .thenThrow(new SignatureVerificationException("bad signature", "sig"));

        ResponseEntity<String> resp = controller.handleStripeWebhook("{}", "t=1,v1=xx");

        assertEquals(200, resp.getStatusCode().value(),
                "签名错误是确定性失败，返回非 2xx 会让 Stripe 重试 3 天");
        assertTrue(resp.getBody().contains("invalid signature"));
        // 不应触及业务处理
        verify(paymentService, never()).handlePaymentSuccess(any());
    }

    @Test
    @DisplayName("事件构造失败（payload 畸形）返回 2xx")
    void malformedEventReturns2xx() throws Exception {
        when(paymentService.constructEvent(anyString(), anyString()))
                .thenThrow(new IllegalStateException("not json"));

        ResponseEntity<String> resp = controller.handleStripeWebhook("garbage", "sig");

        assertEquals(200, resp.getStatusCode().value());
        assertTrue(resp.getBody().contains("malformed"));
    }

    @Test
    @DisplayName("事件数据结构不符（不是 Session）返回 2xx")
    void wrongDataObjectReturns2xx() throws Exception {
        Event event = fakeEvent("checkout.session.completed", "evt_1");
        // deserializeUnsafe 返回一个非 Session 对象
        EventDataObjectDeserializer d = event.getDataObjectDeserializer();
        doReturn(mock(StripeObject.class)).when(d).deserializeUnsafe();
        when(paymentService.constructEvent(anyString(), anyString())).thenReturn(event);

        ResponseEntity<String> resp = controller.handleStripeWebhook("{}", "sig");

        assertEquals(200, resp.getStatusCode().value(),
                "数据结构不符是确定性失败，不应触发重试");
        verify(paymentService, never()).handlePaymentSuccess(any());
    }

    // ---------- 可恢复：必须 5xx ----------

    @Test
    @DisplayName("【关键】业务处理抛异常返回 5xx，让 Stripe 重试")
    void businessErrorReturns5xx() throws Exception {
        Event event = fakeEvent("checkout.session.completed", "evt_2");
        EventDataObjectDeserializer d = event.getDataObjectDeserializer();
        doReturn(Optional.empty()).when(d).getObject();
        doReturn(mock(com.stripe.model.checkout.Session.class)).when(d).deserializeUnsafe();
        when(paymentService.constructEvent(anyString(), anyString())).thenReturn(event);

        // 模拟数据库超时这类瞬时故障
        doThrow(new RuntimeException("db timeout"))
                .when(paymentService).handlePaymentSuccess(any());

        ResponseEntity<String> resp = controller.handleStripeWebhook("{}", "sig");

        assertEquals(500, resp.getStatusCode().value(),
                "瞬时故障应返回 5xx，让 Stripe 重新投递");
        assertTrue(resp.getBody().contains("retryable"));
    }

    // ---------- 正常路径 ----------

    @Test
    @DisplayName("未订阅的事件类型返回 2xx 且不调用业务")
    void unknownEventTypeReturns2xx() throws Exception {
        // 注意：必须先构造 Event，再 stub constructEvent。
        // 若写成 when(...).thenReturn(fakeEvent(...))，
        // fakeEvent 内部调 mock() 会打断 when() 的求值，触发 UnfinishedStubbingException。
        Event event = fakeEvent("customer.created", "evt_3");
        when(paymentService.constructEvent(anyString(), anyString())).thenReturn(event);

        ResponseEntity<String> resp = controller.handleStripeWebhook("{}", "sig");

        assertEquals(200, resp.getStatusCode().value());
        assertEquals("success", resp.getBody());
        verify(paymentService, never()).handlePaymentSuccess(any());
    }

    @Test
    @DisplayName("支付成功事件正常处理并返回 2xx")
    void successEventReturns2xx() throws Exception {
        Event event = fakeEvent("checkout.session.completed", "evt_4");
        EventDataObjectDeserializer d = event.getDataObjectDeserializer();
        doReturn(Optional.of(mock(com.stripe.model.checkout.Session.class))).when(d).getObject();
        when(paymentService.constructEvent(anyString(), anyString())).thenReturn(event);

        ResponseEntity<String> resp = controller.handleStripeWebhook("{}", "sig");

        assertEquals(200, resp.getStatusCode().value());
        assertEquals("success", resp.getBody());
        verify(paymentService).handlePaymentSuccess(any());
    }

    @Test
    @DisplayName("异步支付成功事件同样被处理")
    void asyncPaymentSucceededHandled() throws Exception {
        Event event = fakeEvent("checkout.session.async_payment_succeeded", "evt_5");
        EventDataObjectDeserializer d = event.getDataObjectDeserializer();
        doReturn(Optional.of(mock(com.stripe.model.checkout.Session.class))).when(d).getObject();
        when(paymentService.constructEvent(anyString(), anyString())).thenReturn(event);

        ResponseEntity<String> resp = controller.handleStripeWebhook("{}", "sig");

        assertEquals(200, resp.getStatusCode().value());
        verify(paymentService).handlePaymentSuccess(any());
    }
}
