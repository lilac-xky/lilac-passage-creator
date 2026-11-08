package com.lilac.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步配置类
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 文章生成异步线程池
     *
     * <p>承载的是「一个文章阶段」的完整流程（含多次 LLM 调用），
     * 属于长耗时任务，故队列给得较大、拒绝策略为调用方执行。
     */
    @Bean(name = "articleExecutor")
    public Executor articleExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数
        executor.setCorePoolSize(5);
        // 最大线程数
        executor.setMaxPoolSize(10);
        // 队列容量
        executor.setQueueCapacity(100);
        // 线程名称前缀
        executor.setThreadNamePrefix("article-async-");
        // 拒绝策略：由调用线程处理
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 等待所有任务完成后再关闭线程池
        executor.setWaitForTasksToCompleteOnShutdown(true);
        // 等待时间
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();
        return executor;
    }

    /**
     * 配图并行生成专用线程池
     *
     * <p><b>为什么需要这个池</b>
     *
     * <p>改造前 {@code ParallelImageGenerator} 用的是
     * {@code CompletableFuture.runAsync(...)} 不传 executor 的重载，
     * 任务会落到 {@link java.util.concurrent.ForkJoinPool#commonPool()}。
     * 这有两个问题：
     *
     * <ol>
     *   <li><b>池语义错配</b>：commonPool 是为 <b>CPU 密集型</b>任务设计的，
     *       并行度默认为 {@code CPU核数 - 1}。而这里的任务是 HTTP 请求
     *       （Pexels / Gemini）加 COS 上传，全是 <b>阻塞 IO</b>。
     *       用 CPU 池跑阻塞 IO，会让本机可用并行度被严重低估。</li>
     *   <li><b>污染全局池</b>：commonPool 是 JVM 全局共享的，
     *       并行流（{@code parallelStream()}）等也会用它。
     *       图片任务把 commonPool 占满，会拖慢/饿死进程内其它使用方。</li>
     * </ol>
     *
     * <p>改用专用池后，阻塞 IO 与 CPU 计算隔离，且线程数可按 IO 密集场景配置
     * （IO 密集型通常可以比核数大得多，因为线程大部分时间在等网络）。
     *
     * <p>拒绝策略仍用 {@link ThreadPoolExecutor.CallerRunsPolicy}：
     * 池满时由提交方（article-async 线程）自己执行，形成天然背压，
     * 而不是抛 RejectedExecutionException 让整个配图阶段失败。
     */
    @Bean(name = "imageExecutor")
    public Executor imageExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数：覆盖常见的「一次 3~6 个图源分组」
        executor.setCorePoolSize(8);
        // 最大线程数：IO 密集型可远高于 CPU 核数（线程大部分时间在等网络）。
        // 注意必须显著大于核数 —— 否则就退化成 CPU 池的语义，
        // 失去了从 commonPool 独立出来的意义。
        executor.setMaxPoolSize(32);
        // 队列容量
        executor.setQueueCapacity(100);
        // 线程名称前缀：便于在线程栈 / 日志里识别
        executor.setThreadNamePrefix("image-gen-");
        // 拒绝策略：调用方执行，形成背压而非直接失败
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(120);

        executor.initialize();
        return executor;
    }
}
