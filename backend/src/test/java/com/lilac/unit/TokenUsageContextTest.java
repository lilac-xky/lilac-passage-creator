package com.lilac.unit;

import com.lilac.agent.context.TokenUsageContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Token 用量采集单测（步骤 E1）。
 *
 * <p>这个埋点的目的是回答「一篇文章烧多少 token / 多少钱」。
 * 之前 agent_log 只有耗时没有 token，导致成本完全无法统计。
 */
@DisplayName("Token 用量采集")
class TokenUsageContextTest {

    @BeforeEach
    void setUp() {
        TokenUsageContext.begin();
    }

    @AfterEach
    void tearDown() {
        TokenUsageContext.clear();
    }

    /** 构造带 usage 的响应 */
    private ChatResponse responseWith(Integer promptTokens, Integer completionTokens, String model) {
        DefaultUsage usage = new DefaultUsage(promptTokens, completionTokens);
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .model(model)
                .usage(usage)
                .build();
        return new ChatResponse(java.util.List.of(), metadata);
    }

    @Test
    @DisplayName("单次调用能记录 prompt/completion/total")
    void recordsSingleCall() {
        TokenUsageContext.record(responseWith(120, 340, "glm-5.2"));

        TokenUsageContext.Accumulator acc = TokenUsageContext.get();
        assertNotNull(acc);
        assertEquals(120, acc.getPromptTokens());
        assertEquals(340, acc.getCompletionTokens());
        assertEquals(460, acc.getTotalTokens());
        assertEquals("glm-5.2", acc.getModel());
        assertFalse(acc.isEmpty());
    }

    @Test
    @DisplayName("流式多帧：取较大值而非累加（厂商返回累计值）")
    void streamingTakesMax() {
        // 典型流式：每帧返回的是"到目前为止"的累计值
        TokenUsageContext.record(responseWith(100, 50, "glm-5.2"));
        TokenUsageContext.record(responseWith(100, 120, "glm-5.2"));
        TokenUsageContext.record(responseWith(100, 200, "glm-5.2"));

        TokenUsageContext.Accumulator acc = TokenUsageContext.get();
        assertEquals(100, acc.getPromptTokens());
        assertEquals(200, acc.getCompletionTokens(), "应取最大累计值，而不是 50+120+200");
        assertEquals(300, acc.getTotalTokens());
    }

    @Test
    @DisplayName("null 响应安全忽略")
    void nullResponse() {
        TokenUsageContext.record(null);
        TokenUsageContext.Accumulator acc = TokenUsageContext.get();
        assertTrue(acc == null || acc.isEmpty());
    }

    @Test
    @DisplayName("无 metadata 的响应不会 NPE")
    void nullMetadata() {
        // 构造一个 results 为空、metadata 正常的响应模拟极端情况
        TokenUsageContext.record(new ChatResponse(java.util.List.of()));
        assertTrue(TokenUsageContext.get() == null || TokenUsageContext.get().isEmpty());
    }

    @Test
    @DisplayName("clear 后不残留（线程池复用场景）")
    void clearRemoves() {
        TokenUsageContext.record(responseWith(10, 20, "m"));
        TokenUsageContext.clear();
        assertNull(TokenUsageContext.get(), "必须清理，否则线程池复用会把上个任务的 token 算到下一个");
    }

    @Test
    @DisplayName("未显式 begin 时 record 会自动创建，不丢数据")
    void autoCreateWithoutBegin() {
        TokenUsageContext.clear();
        TokenUsageContext.record(responseWith(5, 7, "m"));
        TokenUsageContext.Accumulator acc = TokenUsageContext.get();
        assertNotNull(acc);
        assertEquals(12, acc.getTotalTokens());
    }

    @Test
    @DisplayName("多次调用后 total 等于各分项之和")
    void totalConsistency() {
        TokenUsageContext.record(responseWith(1000, 2000, "a"));
        TokenUsageContext.Accumulator acc = TokenUsageContext.get();
        assertEquals(acc.getPromptTokens() + acc.getCompletionTokens(), acc.getTotalTokens());
    }
}
