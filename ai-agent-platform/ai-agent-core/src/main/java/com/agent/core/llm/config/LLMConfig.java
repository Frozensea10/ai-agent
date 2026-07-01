package com.agent.core.llm.config;

import com.agent.core.llm.adapter.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class LLMConfig {

    @Value("${llm.openai.api-key:}")
    private String openaiApiKey;

    @Value("${llm.qwen.api-key:}")
    private String qwenApiKey;

    @Value("${llm.anthropic.api-key:}")
    private String anthropicApiKey;

    @Value("${llm.deepseek.api-key:}")
    private String deepseekApiKey;

    @Bean
    public Map<String, ModelAdapter> modelAdapterMap() {
        Map<String, ModelAdapter> adapters = new HashMap<>();
        if (openaiApiKey != null && !openaiApiKey.isBlank()) {
            adapters.put("openai", new OpenAiAdapter(openaiApiKey));
        }
        if (qwenApiKey != null && !qwenApiKey.isBlank()) {
            adapters.put("qwen", new QwenAdapter(qwenApiKey));
        }
        if (anthropicApiKey != null && !anthropicApiKey.isBlank()) {
            adapters.put("anthropic", new AnthropicAdapter(anthropicApiKey));
        }
        if (deepseekApiKey != null && !deepseekApiKey.isBlank()) {
            adapters.put("deepseek", new DeepSeekAdapter(deepseekApiKey));
        }
        return adapters;
    }
}
