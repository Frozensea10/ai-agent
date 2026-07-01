package com.agent.core.llm.adapter;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

import static com.agent.core.llm.adapter.LlmAdapterConstants.*;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;

@Component
public class OpenAiAdapter implements ModelAdapter {

    private static final String MODEL_GPT_4O = "gpt-4o";
    private static final String MODEL_GPT_4O_MINI = "gpt-4o-mini";

    private final String apiKey;

    public OpenAiAdapter(@Value("${llm.openai.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public String getProvider() {
        return PROVIDER_OPENAI;
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public ChatModel createChatModel(String modelName, Double temperature, Integer maxTokens) {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(resolveModelName(modelName))
                .temperature(temperature != null ? temperature : DEFAULT_TEMPERATURE)
                .maxTokens(maxTokens != null ? maxTokens : DEFAULT_MAX_TOKENS)
                .timeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS))
                .maxRetries(DEFAULT_MAX_RETRIES)
                .build();
    }

    @Override
    public StreamingChatModel createStreamingModel(String modelName, Double temperature, Integer maxTokens) {
        return OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .modelName(resolveModelName(modelName))
                .temperature(temperature != null ? temperature : DEFAULT_TEMPERATURE)
                .maxTokens(maxTokens != null ? maxTokens : DEFAULT_MAX_TOKENS)
                .timeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS))
                .build();
    }

    private String resolveModelName(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return GPT_4_O_MINI.toString();
        }
        return switch (modelName.toLowerCase()) {
            case MODEL_GPT_4O -> GPT_4_O.toString();
            case MODEL_GPT_4O_MINI -> GPT_4_O_MINI.toString();
            default -> modelName;
        };
    }
}
