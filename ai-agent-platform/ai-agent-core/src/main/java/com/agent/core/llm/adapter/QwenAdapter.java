package com.agent.core.llm.adapter;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.community.model.dashscope.QwenStreamingChatModel;
import org.springframework.stereotype.Component;

import static com.agent.core.llm.adapter.LlmAdapterConstants.*;

@Component
public class QwenAdapter implements ModelAdapter {

    @Override
    public String getProvider() {
        return PROVIDER_QWEN;
    }

    @Override
    public ChatModel createChatModel(String apiKey, String modelName, Double temperature, Integer maxTokens) {
        return QwenChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName != null ? modelName : MODEL_QWEN_TURBO)
                .temperature(toFloatTemperature(temperature))
                .maxTokens(maxTokens)
                .build();
    }

    @Override
    public StreamingChatModel createStreamingModel(String apiKey, String modelName, Double temperature, Integer maxTokens) {
        return QwenStreamingChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName != null ? modelName : MODEL_QWEN_TURBO)
                .temperature(toFloatTemperature(temperature))
                .maxTokens(maxTokens)
                .build();
    }
}
