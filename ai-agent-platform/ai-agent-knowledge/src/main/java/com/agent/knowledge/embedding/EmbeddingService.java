package com.agent.knowledge.embedding;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static com.agent.knowledge.embedding.EmbeddingConstants.*;

@Slf4j
@Component
public class EmbeddingService {

    @Value("${llm.embedding.provider:openai}")
    private String provider;

    @Value("${llm.embedding.model:text-embedding-3-small}")
    private String modelName;

    @Value("${llm.embedding.api-key:}")
    private String apiKey;

    @Value("${llm.embedding.timeout-seconds:60}")
    private long timeoutSeconds;

    @Value("${llm.embedding.batch-size:100}")
    private int batchSize;

    private EmbeddingModel embeddingModel;

    @PostConstruct
    public void init() {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Embedding API key not configured, embedding service will not be available");
            return;
        }
        embeddingModel = OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .build();
        log.info("Embedding model initialized: {} - {}", provider, modelName);
    }

    public List<Float> embed(String text) {
        checkAvailable();
        return embeddingModel.embed(text).content().vectorAsList();
    }

    public List<List<Float>> embedBatch(List<String> texts) {
        checkAvailable();
        if (texts == null || texts.isEmpty()) {
            return new ArrayList<>();
        }

        List<List<Float>> allEmbeddings = new ArrayList<>(texts.size());

        for (int i = 0; i < texts.size(); i += batchSize) {
            List<String> batch = texts.subList(i, Math.min(i + batchSize, texts.size()));
            try {
                List<TextSegment> segments = batch.stream()
                        .map(TextSegment::from)
                        .toList();
                List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
                for (Embedding embedding : embeddings) {
                    allEmbeddings.add(embedding.vectorAsList());
                }
                log.info("Batch embedding progress: {}/{} chunks", Math.min(i + batchSize, texts.size()), texts.size());
            } catch (Exception e) {
                log.error("Batch embedding failed for chunk {}-{}", i, Math.min(i + batchSize, texts.size()), e);
                allEmbeddings.addAll(embedBatchFallback(batch));
            }
        }

        return allEmbeddings;
    }

    private List<List<Float>> embedBatchFallback(List<String> batch) {
        List<List<Float>> fallbackEmbeddings = new ArrayList<>(batch.size());
        for (String text : batch) {
            try {
                fallbackEmbeddings.add(embeddingModel.embed(text).content().vectorAsList());
            } catch (Exception ex) {
                log.error("Single embedding failed for text: {}", text.substring(0, Math.min(LOG_TEXT_MAX_LENGTH, text.length())), ex);
                fallbackEmbeddings.add(new ArrayList<>());
            }
        }
        return fallbackEmbeddings;
    }

    private void checkAvailable() {
        if (embeddingModel == null) {
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR.getCode(), "Embedding model not initialized");
        }
    }

    public int getVectorSize() {
        return switch (modelName) {
            case MODEL_TEXT_EMBEDDING_3_LARGE -> EMBEDDING_SIZE_3_LARGE;
            case MODEL_TEXT_EMBEDDING_3_SMALL -> EMBEDDING_SIZE_3_SMALL;
            default -> EMBEDDING_SIZE_3_SMALL;
        };
    }

    public boolean isAvailable() {
        return embeddingModel != null;
    }
}
