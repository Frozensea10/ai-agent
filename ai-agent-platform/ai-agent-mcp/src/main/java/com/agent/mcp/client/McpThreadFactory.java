package com.agent.mcp.client;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MCP 客户端线程池工厂。
 *
 * <p>手册强制禁用 {@code Executors} 便捷方法（其内部使用无界队列，有 OOM 风险），
 * 统一通过本类显式构造有界线程池，并使用命名线程工厂便于排查。
 */
public final class McpThreadFactory {

    /** 单线程池任务队列容量。 */
    private static final int QUEUE_CAPACITY = 100;

    private McpThreadFactory() {
    }

    /**
     * 构造命名线程工厂，生成守护线程。
     *
     * @param namePrefix 线程名前缀
     * @return 线程工厂
     */
    public static ThreadFactory namedDaemonThreadFactory(String namePrefix) {
        AtomicInteger sequence = new AtomicInteger(0);
        return r -> {
            Thread t = new Thread(r, namePrefix + "-" + sequence.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }

    /**
     * 构造单线程有界任务执行器（用于串行读写等场景）。
     *
     * @param namePrefix 线程名前缀
     * @return 有界单线程执行器
     */
    public static ExecutorService newBoundedSingleThreadExecutor(String namePrefix) {
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                namedDaemonThreadFactory(namePrefix),
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    /**
     * 构造单线程定时任务执行器（用于心跳/监控等周期任务）。
     *
     * @param namePrefix 线程名前缀
     * @return 定时任务执行器
     */
    public static ScheduledThreadPoolExecutor newSingleThreadScheduledExecutor(String namePrefix) {
        ScheduledThreadPoolExecutor executor =
                new ScheduledThreadPoolExecutor(1, namedDaemonThreadFactory(namePrefix));
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        return executor;
    }
}
