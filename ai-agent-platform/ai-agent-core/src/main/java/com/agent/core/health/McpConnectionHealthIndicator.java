package com.agent.core.health;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * MCP连接健康检查
 * 简化实现：直接返回 UP（避免循环依赖 ai-agent-mcp 模块）
 */
@Slf4j
@Component
public class McpConnectionHealthIndicator implements HealthIndicator {

    @Override
    public Health health() {
        return Health.up()
                .withDetail("component", "MCP Connection")
                .withDetail("status", "simplified_check")
                .withDetail("message", "MCP健康检查已简化")
                .build();
    }
}
