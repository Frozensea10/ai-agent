package com.agent.core.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步线程池配置
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Value("${async.task.core-pool-size:4}")
    private int taskCorePoolSize;

    @Value("${async.task.max-pool-size:16}")
    private int taskMaxPoolSize;

    @Value("${async.task.queue-capacity:200}")
    private int taskQueueCapacity;

    @Value("${async.task.keep-alive-seconds:60}")
    private int taskKeepAliveSeconds;

    @Value("${async.sub-agent.core-pool-size:8}")
    private int subAgentCorePoolSize;

    @Value("${async.sub-agent.max-pool-size:32}")
    private int subAgentMaxPoolSize;

    @Value("${async.sub-agent.queue-capacity:500}")
    private int subAgentQueueCapacity;

    @Value("${async.sub-agent.keep-alive-seconds:120}")
    private int subAgentKeepAliveSeconds;

    @Value("${async.knowledge.core-pool-size:2}")
    private int knowledgeCorePoolSize;

    @Value("${async.knowledge.max-pool-size:8}")
    private int knowledgeMaxPoolSize;

    @Value("${async.knowledge.queue-capacity:100}")
    private int knowledgeQueueCapacity;

    @Value("${async.knowledge.keep-alive-seconds:300}")
    private int knowledgeKeepAliveSeconds;

    @Value("${async.await-termination-seconds:60}")
    private int awaitTerminationSeconds;

    /**
     * 通用异步任务线程池
     * 用于：MCP工具调用、文档处理、消息发送
     */
    @Bean("taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(taskCorePoolSize);
        executor.setMaxPoolSize(taskMaxPoolSize);
        executor.setQueueCapacity(taskQueueCapacity);
        executor.setThreadNamePrefix("async-task-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setKeepAliveSeconds(taskKeepAliveSeconds);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(awaitTerminationSeconds);
        executor.initialize();
        return executor;
    }

    /**
     * 子Agent执行专用线程池
     * 用于：多Agent协作时的并行子任务执行
     */
    @Bean("subAgentExecutor")
    public Executor subAgentExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(subAgentCorePoolSize);
        executor.setMaxPoolSize(subAgentMaxPoolSize);
        executor.setQueueCapacity(subAgentQueueCapacity);
        executor.setThreadNamePrefix("sub-agent-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setKeepAliveSeconds(subAgentKeepAliveSeconds);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(awaitTerminationSeconds);
        executor.initialize();
        return executor;
    }

    /**
     * 知识库处理线程池
     * 用于：文档解析、向量化、索引构建
     */
    @Bean("knowledgeExecutor")
    public Executor knowledgeExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(knowledgeCorePoolSize);
        executor.setMaxPoolSize(knowledgeMaxPoolSize);
        executor.setQueueCapacity(knowledgeQueueCapacity);
        executor.setThreadNamePrefix("knowledge-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setKeepAliveSeconds(knowledgeKeepAliveSeconds);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(awaitTerminationSeconds);
        executor.initialize();
        return executor;
    }
}
