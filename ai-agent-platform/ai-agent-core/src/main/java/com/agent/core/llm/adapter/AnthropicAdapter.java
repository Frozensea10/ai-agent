package com.agent.core.llm.adapter;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

import static com.agent.core.llm.adapter.LlmAdapterConstants.*;

@Component
public class AnthropicAdapter implements ModelAdapter {

    private final String apiKey;

    public AnthropicAdapter(@Value("${llm.anthropic.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public String getProvider() {
        return PROVIDER_ANTHROPIC;
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public ChatModel createChatModel(String modelName, Double temperature, Integer maxTokens) {
        return AnthropicChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName != null ? modelName : MODEL_CLAUDE_SONNET)
                .temperature(temperature != null ? temperature : DEFAULT_TEMPERATURE)
                .maxTokens(maxTokens != null ? maxTokens : DEFAULT_MAX_TOKENS)
                .timeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS))
                .maxRetries(DEFAULT_MAX_RETRIES)
                .build();
    }

    @Override
    public StreamingChatModel createStreamingModel(String modelName, Double temperature, Integer maxTokens) {
        return AnthropicStreamingChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName != null ? modelName : MODEL_CLAUDE_SONNET)
                .temperature(temperature != null ? temperature : DEFAULT_TEMPERATURE)
                .maxTokens(maxTokens != null ? maxTokens : DEFAULT_MAX_TOKENS)
                .timeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS))
                .build();
    }
}
