package com.lilac.unit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 配图线程池配置单测（步骤 C3）。
 *
 * <p>核心回归点：{@link ForkJoinPool#commonPool()} 不能承载阻塞 IO，
 * 必须使用专用池。
 */
@DisplayName("配图专用线程池")
class ImageExecutorTest {

    private final com.lilac.config.AsyncConfig config = new com.lilac.config.AsyncConfig();

    private ThreadPoolTaskExecutorCfg getCfg(String beanName) throws Exception {
        Method m = com.lilac.config.AsyncConfig.class.getDeclaredMethod(beanName);
        Executor executor = (Executor) m.invoke(config);
        return new ThreadPoolTaskExecutorCfg(executor);
    }

    /** 从 ThreadPoolTaskExecutor 里取出底层 ThreadPoolExecutor 做断言 */
    private record ThreadPoolTaskExecutorCfg(Executor executor) {
        ThreadPoolExecutor unwrap() {
            var tpte = (org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor) executor;
            return tpte.getThreadPoolExecutor();
        }
    }

    @Test
    @DisplayName("imageExecutor 不是 ForkJoinPool.commonPool")
    void notCommonPool() throws Exception {
        Executor executor = getCfg("imageExecutor").executor();
        assertNotSame(ForkJoinPool.commonPool(), executor,
                "配图任务必须用专用池，否则会污染全局 ForkJoinPool 并低估阻塞 IO 并行度");
    }

    @Test
    @DisplayName("线程名前缀为 image-gen-，便于日志与线程栈识别")
    void threadNamePrefix() throws Exception {
        ThreadPoolExecutor pool = getCfg("imageExecutor").unwrap();
        AtomicReference<String> name = new AtomicReference<>();
        pool.execute(() -> name.set(Thread.currentThread().getName()));
        // 等待任务执行
        for (int i = 0; i < 50 && name.get() == null; i++) {
            Thread.sleep(20);
        }
        assertTrue(name.get() != null && name.get().startsWith("image-gen-"),
                "实际线程名: " + name.get());
    }

    @Test
    @DisplayName("最大线程数足以覆盖 IO 密集场景（> CPU 核数）")
    void maxPoolSizeForIoBound() throws Exception {
        ThreadPoolExecutor pool = getCfg("imageExecutor").unwrap();
        int cores = Runtime.getRuntime().availableProcessors();
        assertTrue(pool.getMaximumPoolSize() > cores,
                "IO 密集型池的最大线程数应大于 CPU 核数，实际 " + pool.getMaximumPoolSize() + " vs 核数 " + cores);
    }

    @Test
    @DisplayName("拒绝策略为 CallerRunsPolicy，形成背压而不是直接失败")
    void callerRunsPolicy() throws Exception {
        ThreadPoolExecutor pool = getCfg("imageExecutor").unwrap();
        assertTrue(pool.getRejectedExecutionHandler() instanceof ThreadPoolExecutor.CallerRunsPolicy,
                "池满时应由调用方执行，避免整个配图阶段因 RejectedExecutionException 失败");
    }

    @Test
    @DisplayName("两个池互不复用：articleExecutor 与 imageExecutor 是不同实例")
    void poolsAreIndependent() throws Exception {
        Executor article = getCfg("articleExecutor").executor();
        Executor image = getCfg("imageExecutor").executor();
        assertNotSame(article, image,
                "配图池与文章阶段池必须隔离，否则长耗时的图源会占满阶段池");
    }

    @Test
    @DisplayName("池配置了优雅关闭：shutdown 会等已提交任务跑完")
    void gracefulShutdown() throws Exception {
        var tpte = (org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor) getCfg("imageExecutor").executor();
        java.util.concurrent.CountDownLatch started = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean finished = new java.util.concurrent.atomic.AtomicBoolean(false);

        tpte.execute(() -> {
            started.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
                finished.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        assertTrue(started.await(2, TimeUnit.SECONDS), "任务应已开始执行");

        // 触发关闭（不阻塞），然后放行任务
        Thread shutdownThread = new Thread(tpte::shutdown);
        shutdownThread.start();
        release.countDown();

        shutdownThread.join(5000);
        assertTrue(finished.get(), "优雅关闭应等待已提交任务执行完毕，而不是直接中断");
    }

    private static void sleep(long ms) {
        try {
            TimeUnit.MILLISECONDS.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
