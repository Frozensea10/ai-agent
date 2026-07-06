package com.agent.core.llm.adapter;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;

import java.time.Duration;

import static com.agent.core.llm.adapter.LlmAdapterConstants.*;

public class DeepSeekAdapter implements ModelAdapter {

    @Override
    public String getProvider() {
        return PROVIDER_DEEPSEEK;
    }

    @Override
    public ChatModel createChatModel(String apiKey, String modelName, Double temperature, Integer maxTokens) {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(DEEPSEEK_BASE_URL)
                .modelName(resolveModelName(modelName))
                .temperature(temperature != null ? temperature : DEFAULT_TEMPERATURE)
                .maxTokens(maxTokens != null ? maxTokens : DEFAULT_MAX_TOKENS)
                .timeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS))
                .maxRetries(DEFAULT_MAX_RETRIES)
                .build();
    }

    @Override
    public StreamingChatModel createStreamingModel(String apiKey, String modelName, Double temperature, Integer maxTokens) {
        return OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(DEEPSEEK_BASE_URL)
                .modelName(resolveModelName(modelName))
                .temperature(temperature != null ? temperature : DEFAULT_TEMPERATURE)
                .maxTokens(maxTokens != null ? maxTokens : DEFAULT_MAX_TOKENS)
                .timeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS))
                .build();
    }

    private String resolveModelName(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return MODEL_DEEPSEEK_CHAT;
        }
        return switch (modelName.toLowerCase()) {
            case "deepseek-v4-pro" -> MODEL_DEEPSEEK_PRO;
            case "deepseek-v4-flash" -> MODEL_DEEPSEEK_CHAT;
            default -> modelName;
        };
    }
}
