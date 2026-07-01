package com.agent.core.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * 业务指标收集器
 * 收集AI Agent核心业务指标
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentMetrics {

    private final MeterRegistry meterRegistry;

    private Counter chatRequestCounter;
    private Counter toolCallCounter;
    private Counter collaborationCounter;
    private Timer chatResponseTimer;
    private Timer toolExecutionTimer;
    private Timer collaborationTimer;

    @PostConstruct
    public void init() {
        // 对话请求计数器
        chatRequestCounter = Counter.builder("agent.chat.requests")
                .description("对话请求总数")
                .register(meterRegistry);

        // 工具调用计数器
        toolCallCounter = Counter.builder("agent.tool.calls")
                .description("工具调用总数")
                .register(meterRegistry);

        // 多Agent协作计数器
        collaborationCounter = Counter.builder("agent.collaboration.requests")
                .description("多Agent协作请求总数")
                .register(meterRegistry);

        // 对话响应时间
        chatResponseTimer = Timer.builder("agent.chat.response.time")
                .description("对话响应时间")
                .register(meterRegistry);

        // 工具执行时间
        toolExecutionTimer = Timer.builder("agent.tool.execution.time")
                .description("工具执行时间")
                .register(meterRegistry);

        // 协作执行时间
        collaborationTimer = Timer.builder("agent.collaboration.time")
                .description("多Agent协作执行时间")
                .register(meterRegistry);

        log.info("业务指标收集器初始化完成");
    }

    /**
     * 记录对话请求
     */
    public void recordChatRequest() {
        chatRequestCounter.increment();
    }

    /**
     * 记录工具调用
     */
    public void recordToolCall() {
        toolCallCounter.increment();
    }

    /**
     * 记录多Agent协作请求
     */
    public void recordCollaboration() {
        collaborationCounter.increment();
    }

    /**
     * 记录对话响应时间
     */
    public void recordChatResponseTime(long millis) {
        chatResponseTimer.record(millis, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * 记录工具执行时间
     */
    public void recordToolExecutionTime(long millis) {
        toolExecutionTimer.record(millis, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * 记录协作执行时间
     */
    public void recordCollaborationTime(long millis) {
        collaborationTimer.record(millis, java.util.concurrent.TimeUnit.MILLISECONDS);
    }
}
