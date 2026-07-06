package com.agent.knowledge.embedding;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.common.result.Result;
import com.agent.knowledge.feign.EmbeddingFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingService {

    /**
     * Embedding 单次批量上限。DashScope text-embedding-v4 等模型单请求最多 10 条文本，
     * OpenAI text-embedding-3-* 也建议小批量调用以避免超时或限流。
     */
    private static final int EMBED_BATCH_SIZE = 10;

    private final EmbeddingFeignClient embeddingFeignClient;

    public List<Float> embed(String text, String provider, String modelName) {
        checkAvailable(provider);
        try {
            Result<List<Float>> result = embeddingFeignClient.embed(provider, modelName, text);
            if (result.isSuccess() && result.getData() != null) {
                return result.getData();
            }
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR.getCode(),
                    "Embedding 服务调用失败: " + result.getMessage());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Embedding 调用失败", e);
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR.getCode(), "Embedding 服务不可用");
        }
    }

    public List<List<Float>> embedBatch(List<String> texts, String provider, String modelName) {
        checkAvailable(provider);
        if (texts == null || texts.isEmpty()) {
            return new ArrayList<>();
        }

        // 分批调用：DashScope text-embedding-v4 等模型单次批量有上限（通常 10 条），
        // 一次性发送上千条会导致提供商返回“系统繁忙”或 422 错误。
        List<List<Float>> all = new ArrayList<>(texts.size());
        for (int from = 0; from < texts.size(); from += EMBED_BATCH_SIZE) {
            int to = Math.min(from + EMBED_BATCH_SIZE, texts.size());
            List<String> batch = texts.subList(from, to);
            try {
                Result<List<List<Float>>> result = embeddingFeignClient.embedBatch(provider, modelName, batch);
                if (!result.isSuccess() || result.getData() == null) {
                    throw new BusinessException(ErrorCode.AI_SERVICE_ERROR.getCode(),
                            "批量 Embedding 服务调用失败 (batch " + (from / EMBED_BATCH_SIZE + 1) + "): " + result.getMessage());
                }
                all.addAll(result.getData());
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                log.error("批量 Embedding 调用失败 (batch from={}, size={})", from, batch.size(), e);
                throw new BusinessException(ErrorCode.AI_SERVICE_ERROR.getCode(), "Embedding 服务不可用: " + e.getMessage());
            }
        }
        return all;
    }

    private void checkAvailable(String provider) {
        try {
            Result<Boolean> result = embeddingFeignClient.isProviderAvailable(provider);
            if (!result.isSuccess() || !Boolean.TRUE.equals(result.getData())) {
                throw new BusinessException(ErrorCode.AI_SERVICE_ERROR.getCode(),
                        "Embedding 提供商 [" + provider + "] 未配置或不可用");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR.getCode(), "Embedding 服务不可用");
        }
    }

    /**
     * 返回指定提供商 + 模型对应的向量维度。
     * 不同提供商/模型维度不同，Qdrant collection 必须按此维度创建，否则插入向量会失败。
     */
    public int getVectorSize(String provider, String modelName) {
        if (provider == null) {
            return 1536;
        }
        switch (provider.trim().toLowerCase()) {
            case "qwen":
                // text-embedding-v4 默认 1024 维
                return 1024;
            case "openai":
                if (modelName != null && modelName.contains("3-large")) {
                    return 3072;
                }
                return 1536;
            default:
                return 1536;
        }
    }

    public boolean isAvailable(String provider) {
        if (provider == null || provider.isBlank()) {
            return false;
        }
        try {
            Result<Boolean> result = embeddingFeignClient.isProviderAvailable(provider);
            return result.isSuccess() && Boolean.TRUE.equals(result.getData());
        } catch (Exception e) {
            return false;
        }
    }
}