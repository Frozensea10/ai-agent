package com.agent.core.llm.adapter;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;

import java.time.Duration;

import static com.agent.core.llm.adapter.LlmAdapterConstants.*;

/**
 * 通义千问对话模型适配器。
 *
 * <p>通义千问兼容 OpenAI 接口格式，通过 DashScope 兼容模式端点
 * {@code https://dashscope.aliyuncs.com/compatible-mode/v1} 调用。
 */
public class QwenAdapter implements ModelAdapter {

    private static final String QWEN_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1";

    @Override
    public String getProvider() {
        return PROVIDER_QWEN;
    }

    @Override
    public ChatModel createChatModel(String apiKey, String modelName, Double temperature, Integer maxTokens) {
        return OpenAiChatModel.builder()
                .baseUrl(QWEN_BASE_URL)
                .apiKey(apiKey)
                .modelName(modelName != null ? modelName : MODEL_QWEN_TURBO)
                .temperature(temperature != null ? temperature : DEFAULT_TEMPERATURE)
                .maxTokens(maxTokens)
                .timeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS))
                .build();
    }

    @Override
    public StreamingChatModel createStreamingModel(String apiKey, String modelName, Double temperature, Integer maxTokens) {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(QWEN_BASE_URL)
                .apiKey(apiKey)
                .modelName(modelName != null ? modelName : MODEL_QWEN_TURBO)
                .temperature(temperature != null ? temperature : DEFAULT_TEMPERATURE)
                .maxTokens(maxTokens)
                .timeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS))
                .build();
    }
}
