package com.agent.mcp.config;

import com.agent.mcp.client.McpThreadFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * MCP 模块异步任务线程池配置。
 *
 * <p>手册强制：异步任务不允许使用 {@code ForkJoinPool.commonPool()} 跑阻塞 IO，
 * 统一注入自定义有界线程池。
 */
@Configuration
public class McpAsyncConfig {

    /** 核心线程数。 */
    private static final int CORE_POOL_SIZE = 2;
    /** 最大线程数。 */
    private static final int MAX_POOL_SIZE = 4;
    /** 任务队列容量。 */
    private static final int QUEUE_CAPACITY = 100;
    /** 空闲线程存活时间（秒）。 */
    private static final int KEEP_ALIVE_SECONDS = 60;

    /**
     * MCP 异步任务执行器（用于 MCP Server 异步加载、延迟重试等）。
     *
     * @return 有界线程池执行器
     */
    @Bean("mcpTaskExecutor")
    public Executor mcpTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAX_POOL_SIZE);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setKeepAliveSeconds(KEEP_ALIVE_SECONDS);
        executor.setThreadFactory(McpThreadFactory.namedDaemonThreadFactory("mcp-async-task"));
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }
}
