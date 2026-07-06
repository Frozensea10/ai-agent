package com.agent.core.llm.adapter;

import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.extern.slf4j.Slf4j;

/**
 * DeepSeek Embedding 适配器。
 *
 * <p>注意：DeepSeek 官方 <strong>不提供</strong> Embedding API，
 * 调用 {@code /v1/embeddings} 端点会返回 404。
 * 因此该适配器不会真正创建嵌入模型：调用 {@link #createEmbeddingModel}
 * 将直接抛出 {@link UnsupportedOperationException}，
 * {@link #isAvailable()} 始终返回 false。</p>
 *
 * <p>该 Bean 仍保留在容器中，仅用于占位与明确提示，
 * 避免运行时因端点不存在而产生难以排查的 404 错误。
 * 如需嵌入能力，请使用 OpenAI 或其他支持的提供商。</p>
 */
@Slf4j
public class DeepSeekEmbeddingAdapter implements EmbeddingAdapter {

    @Override
    public String getProvider() {
        return "deepseek";
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public EmbeddingModel createEmbeddingModel(String apiKey, String modelName) {
        log.warn("DeepSeek 不提供 Embedding API，createEmbeddingModel 被调用但将抛出异常");
        throw new UnsupportedOperationException("DeepSeek 不提供 Embedding API，请使用 OpenAI 或其他支持的提供商");
    }
}
