package com.agent.core.llm.adapter;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

import static com.agent.core.llm.adapter.LlmAdapterConstants.DEFAULT_EMBEDDING_MODEL;
import static com.agent.core.llm.adapter.LlmAdapterConstants.DEFAULT_TIMEOUT_SECONDS;
import static com.agent.core.llm.adapter.LlmAdapterConstants.OPENAI_EMBEDDING_URL;

@Slf4j
public class OpenAiEmbeddingAdapter implements EmbeddingAdapter {

    @Override
    public String getProvider() {
        return "openai";
    }

    @Override
    public EmbeddingModel createEmbeddingModel(String apiKey, String modelName) {
        String name = (modelName != null && !modelName.isBlank()) ? modelName : DEFAULT_EMBEDDING_MODEL;
        log.debug("创建 OpenAI Embedding 模型: {}", name);
        return OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName(name)
                .timeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS))
                .build();
    }
}