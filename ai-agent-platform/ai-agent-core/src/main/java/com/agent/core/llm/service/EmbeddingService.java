package com.agent.core.llm.service;

import dev.langchain4j.model.embedding.EmbeddingModel;

import java.util.List;

public interface EmbeddingService {

    EmbeddingModel createEmbeddingModel(String provider, String modelName);

    List<Float> embed(String provider, String modelName, String text);

    List<List<Float>> embedBatch(String provider, String modelName, List<String> texts);

    boolean isProviderAvailable(String provider);

    void refreshProviderConfig(String provider);
}
