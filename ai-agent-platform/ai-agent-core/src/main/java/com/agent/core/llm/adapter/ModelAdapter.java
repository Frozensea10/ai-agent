package com.agent.core.llm.adapter;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;

public interface ModelAdapter {

    String getProvider();

    ChatModel createChatModel(String modelName, Double temperature, Integer maxTokens);

    StreamingChatModel createStreamingModel(String modelName, Double temperature, Integer maxTokens);

    default boolean isConfigured() {
        return true;
    }
}
