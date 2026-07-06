package com.agent.core.llm.adapter;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;

import java.time.Duration;

import static com.agent.core.llm.adapter.LlmAdapterConstants.*;

public class AnthropicAdapter implements ModelAdapter {

    @Override
    public String getProvider() {
        return PROVIDER_ANTHROPIC;
    }

    @Override
    public ChatModel createChatModel(String apiKey, String modelName, Double temperature, Integer maxTokens) {
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
    public StreamingChatModel createStreamingModel(String apiKey, String modelName, Double temperature, Integer maxTokens) {
        return AnthropicStreamingChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName != null ? modelName : MODEL_CLAUDE_SONNET)
                .temperature(temperature != null ? temperature : DEFAULT_TEMPERATURE)
                .maxTokens(maxTokens != null ? maxTokens : DEFAULT_MAX_TOKENS)
                .timeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS))
                .build();
    }
}
