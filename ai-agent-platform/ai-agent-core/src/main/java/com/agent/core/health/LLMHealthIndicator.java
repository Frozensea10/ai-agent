package com.agent.core.health;

import com.agent.core.llm.service.LLMService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * LLM模型健康检查
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LLMHealthIndicator implements HealthIndicator {

    private final LLMService llmService;

    @Override
    public Health health() {
        try {
            // 检查至少有一个模型提供商可用
            boolean hasAvailableProvider = llmService.isProviderAvailable("openai") 
                    || llmService.isProviderAvailable("deepseek")
                    || llmService.isProviderAvailable("qwen")
                    || llmService.isProviderAvailable("anthropic");

            if (hasAvailableProvider) {
                return Health.up()
                        .withDetail("component", "LLM Service")
                        .withDetail("openai", llmService.isProviderAvailable("openai"))
                        .withDetail("deepseek", llmService.isProviderAvailable("deepseek"))
                        .withDetail("qwen", llmService.isProviderAvailable("qwen"))
                        .withDetail("anthropic", llmService.isProviderAvailable("anthropic"))
                        .build();
            } else {
                return Health.down()
                        .withDetail("component", "LLM Service")
                        .withDetail("reason", "No LLM provider available")
                        .build();
            }
        } catch (Exception e) {
            log.error("LLM健康检查失败", e);
            return Health.down()
                    .withDetail("component", "LLM Service")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
