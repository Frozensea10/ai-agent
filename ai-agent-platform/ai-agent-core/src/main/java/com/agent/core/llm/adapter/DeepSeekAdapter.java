package com.agent.core.llm.adapter;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

import static com.agent.core.llm.adapter.LlmAdapterConstants.*;

@Component
public class DeepSeekAdapter implements ModelAdapter {

    private final String apiKey;

    public DeepSeekAdapter(@Value("${llm.deepseek.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public String getProvider() {
        return PROVIDER_DEEPSEEK;
    }

    @Override
    public ChatModel createChatModel(String modelName, Double temperature, Integer maxTokens) {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(DEEPSEEK_BASE_URL)
                .modelName(modelName != null ? modelName : MODEL_DEEPSEEK_CHAT)
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
                .baseUrl(DEEPSEEK_BASE_URL)
                .modelName(modelName != null ? modelName : MODEL_DEEPSEEK_CHAT)
                .temperature(temperature != null ? temperature : DEFAULT_TEMPERATURE)
                .maxTokens(maxTokens != null ? maxTokens : DEFAULT_MAX_TOKENS)
                .timeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS))
                .build();
    }
}
