package com.agent.core.llm.adapter;

import dev.langchain4j.model.embedding.EmbeddingModel;

public interface EmbeddingAdapter {

    String getProvider();

    EmbeddingModel createEmbeddingModel(String apiKey, String modelName);

    /**
     * 当前适配器是否真正支持 Embedding 能力。
     * 部分提供商（如 DeepSeek）官方不提供 Embedding API，应覆写为返回 false。
     */
    default boolean isAvailable() {
        return true;
    }
}