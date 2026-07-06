package com.agent.core.llm.adapter;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

import static com.agent.core.llm.adapter.LlmAdapterConstants.DEFAULT_TIMEOUT_SECONDS;

/**
 * 通义千问 Embedding 适配器。
 *
 * <p>通义千问的 Embedding API 兼容 OpenAI 接口格式，可通过 OpenAiEmbeddingModel
 * 指定 DashScope 兼容端点 {@code https://dashscope.aliyuncs.com/compatible-mode/v1} 实现。
 *
 * <p>推荐模型：{@code text-embedding-v4}（1024/2048 维，中文优化）。
 * 需在阿里云百炼平台开通服务并获取 API Key。
 */
@Slf4j
public class QwenEmbeddingAdapter implements EmbeddingAdapter {

    private static final String QWEN_EMBEDDING_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    private static final String DEFAULT_QWEN_EMBEDDING_MODEL = "text-embedding-v4";

    @Override
    public String getProvider() {
        return "qwen";
    }

    @Override
    public EmbeddingModel createEmbeddingModel(String apiKey, String modelName) {
        String name = (modelName != null && !modelName.isBlank()) ? modelName : DEFAULT_QWEN_EMBEDDING_MODEL;
        log.debug("创建通义千问 Embedding 模型: {}", name);
        return OpenAiEmbeddingModel.builder()
                .baseUrl(QWEN_EMBEDDING_BASE_URL)
                .apiKey(apiKey)
                .modelName(name)
                .timeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS))
                .build();
    }
}
