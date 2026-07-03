package com.agent.core.llm.config;

import com.agent.core.llm.adapter.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class LLMConfig {

    @Bean
    public Map<String, ModelAdapter> modelAdapterMap() {
        Map<String, ModelAdapter> adapters = new HashMap<>();
        adapters.put("openai", new OpenAiAdapter());
        adapters.put("qwen", new QwenAdapter());
        adapters.put("anthropic", new AnthropicAdapter());
        adapters.put("deepseek", new DeepSeekAdapter());
        return adapters;
    }
}
