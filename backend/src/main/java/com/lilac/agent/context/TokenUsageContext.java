package com.lilac.agent.context;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.metadata.Usage;

/**
 * LLM token 用量采集上下文（步骤 E1）
 *
 * <p><b>为什么需要这个类</b>
 *
 * <p>对 7 篇真实文章做效果分析时发现：{@code agent_log} 只记了
 * {@code durationMs}，<b>完全没有 token 数</b>。
 * 这意味着：
 * <ul>
 *   <li>无法统计单篇文章的 LLM 成本</li>
 *   <li>无法对比「哪个 prompt 版本更省 token」</li>
 *   <li>无法回答「改成流式后 token 变多了吗」</li>
 * </ul>
 *
 * <p><b>为什么用 ThreadLocal 而不是改方法签名</b>
 *
 * <p>token 数只有拿到 {@link ChatResponse} 的地方才知道，
 * 但那个地方是 Agent 内部的 {@code chatModel.call(...)}，
 * 而写日志的地方是 AOP 切面 {@code AgentExecutionAspect}。
 *
 * <p>若要让切面拿到 token，就得改所有 Agent 的返回值结构 ——
 * 而 Agent 实现的是 {@code NodeAction}，返回 {@code Map<String,Object>}
 * 是 StateGraph 的约定，不能随意加字段。
 *
 * <p>所以用 ThreadLocal 旁路传递：Agent 在拿到响应后调用
 * {@link #record(ChatResponse)}，切面在 finally 里读取。
 * 这与项目已有的 {@code StreamHandlerContext} 是同一套思路。
 *
 * <p><b>注意</b>：流式场景下每个 chunk 都带 usage（部分厂商只在最后一帧给），
 * 这里采用「累加取最大值」策略，避免重复计数。
 */
public final class TokenUsageContext {

    /** 单次 Agent 执行内的累计用量 */
    public static final class Accumulator {
        private int promptTokens;
        private int completionTokens;
        private String model;

        public int getPromptTokens() {
            return promptTokens;
        }

        public int getCompletionTokens() {
            return completionTokens;
        }

        public int getTotalTokens() {
            return promptTokens + completionTokens;
        }

        public String getModel() {
            return model;
        }

        public boolean isEmpty() {
            return promptTokens == 0 && completionTokens == 0;
        }
    }

    private static final ThreadLocal<Accumulator> HOLDER = new ThreadLocal<>();

    private TokenUsageContext() {
    }

    /**
     * 开始一次采集（在 Agent 方法入口调用，或由切面自动调用）。
     */
    public static void begin() {
        HOLDER.set(new Accumulator());
    }

    /**
     * 记录一次 LLM 响应的 token 用量。
     *
     * <p>可在流式循环里反复调用：
     * 非流式只有一次；流式时部分厂商每帧都返回累计值，
     * 所以这里用「取较大值」而非简单累加，避免重复计数。
     *
     * @param response LLM 响应，可为 null
     */
    public static void record(ChatResponse response) {
        if (response == null) {
            return;
        }
        Accumulator acc = HOLDER.get();
        if (acc == null) {
            // 没显式 begin 时自动创建，保证不丢数据
            acc = new Accumulator();
            HOLDER.set(acc);
        }

        try {
            if (response.getMetadata() != null) {
                Usage usage = response.getMetadata().getUsage();
                if (usage != null) {
                    Integer pt = usage.getPromptTokens();
                    Integer ct = usage.getCompletionTokens();
                    if (pt != null && pt > acc.promptTokens) {
                        acc.promptTokens = pt;
                    }
                    if (ct != null && ct > acc.completionTokens) {
                        acc.completionTokens = ct;
                    }
                }
                if (response.getMetadata().getModel() != null) {
                    acc.model = response.getMetadata().getModel();
                }
            }
        } catch (Exception ignored) {
            // token 采集是"锦上添花"，绝不能因为它失败影响主流程
        }
    }

    /**
     * 获取当前线程的累计用量，无则返回 null。
     */
    public static Accumulator get() {
        return HOLDER.get();
    }

    /**
     * 清理。务必在结束处调用，避免线程池复用导致数据串到下一个任务。
     */
    public static void clear() {
        HOLDER.remove();
    }
}
