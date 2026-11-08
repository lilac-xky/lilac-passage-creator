package com.lilac.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * 智能体配置
 */
@Configuration
@Getter
public class AgentConfig {

    /**
     * 是否启用多智能体编排器
     * true: 使用新的 Spring AI Alibaba 多智能体编排
     * false: 使用原有的 ArticleAgentService
     */
    @Value("${article.agent.orchestrator.enabled:true}")
    private boolean orchestratorEnabled;

    /**
     * Agent 最大迭代次数
     */
    @Value("${article.agent.max-iterations:10}")
    private int maxIterations;

    /*
     * ── 已移除：MemorySaver Bean（步骤 C7）──────────────────────────────
     *
     * 原有代码：
     *     @Bean
     *     public MemorySaver memorySaver() {
     *         return new MemorySaver();
     *     }
     * 注释写的是「提供内存状态保存器（单例），用于 Agent 对话记忆管理」。
     *
     * 但核实后发现它**从未被使用**：
     * ArticleAgentOrchestrator 的三处构图都是
     *     CompiledGraph compiledGraph = graph.compile();
     * 调用的是无参 compile()，不注入任何 checkpoint saver。
     * 全仓搜索 MemorySaver / memorySaver 只命中本类，无任何注入点。
     *
     * 这意味着两点：
     * 1. 它是一个纯死 Bean —— 不产生任何记忆，只白占一个单例对象；
     * 2. 更危险的是它**伪装成「已经有记忆管理」**。
     *    如果未来有人真把它接进 compile(saver)，
     *    会立刻变成「所有任务共用一个全局 checkpoint」的跨任务状态污染
     *    （该 MemorySaver 本身没有任何按 taskId 分桶的逻辑）。
     *
     * 处理方式：直接删除，而不是改成「按 taskId 隔离」——
     * 当前没有任何代码需要记忆，为不存在的需求写隔离逻辑属于过度设计。
     * 若以后真要支持「断点续跑 / 跨请求对话记忆」，
     * 应引入按 taskId 分区的 saver，并在 compile() 时显式传入；
     * 那时重新设计，而不是留一个误导性的空壳。
     */
}
