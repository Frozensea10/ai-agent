package com.agent.core.llm.service;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.core.llm.adapter.EmbeddingAdapter;
import com.agent.core.service.LlmProviderConfigService;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private static final String CACHE_KEY_FORMAT = "%s:%s";

    private final Map<String, EmbeddingAdapter> embeddingAdapterMap;
    private final LlmProviderConfigService llmProviderConfigService;
    private final Map<String, EmbeddingModel> embeddingModelCache = new ConcurrentHashMap<>();
    private final Map<String, Boolean> providerAvailability = new ConcurrentHashMap<>();

    public EmbeddingModel createEmbeddingModel(String provider, String modelName) {
        validateProvider(provider);
        String cacheKey = buildCacheKey(provider, modelName);
        return embeddingModelCache.computeIfAbsent(cacheKey, k -> {
            EmbeddingAdapter adapter = getAdapter(provider);
            return adapter.createEmbeddingModel(getApiKey(provider), modelName);
        });
    }

    public List<Float> embed(String provider, String modelName, String text) {
        EmbeddingModel model = createEmbeddingModel(provider, modelName);
        return model.embed(text).content().vectorAsList();
    }

    public List<List<Float>> embedBatch(String provider, String modelName, List<String> texts) {
        EmbeddingModel model = createEmbeddingModel(provider, modelName);
        // 注意：不使用 model.embedAll()，因为 DashScope OpenAI 兼容模式对批量 input 数组
        // 兼容性差（即使 1 条也返回"系统繁忙"）。改为循环单条 embed，确保兼容性。
        List<List<Float>> result = new java.util.ArrayList<>(texts.size());
        for (String text : texts) {
            result.add(model.embed(text).content().vectorAsList());
        }
        return result;
    }

    private void validateProvider(String provider) {
        if (!isProviderAvailable(provider)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR.getCode(),
                    "Embedding 提供商 [" + provider + "] 未配置或不可用");
        }
    }

    private String buildCacheKey(String provider, String modelName) {
        return String.format(CACHE_KEY_FORMAT, provider, modelName);
    }

    private EmbeddingAdapter getAdapter(String provider) {
        if (provider == null || provider.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "Embedding 提供商不能为空");
        }
        String normalized = provider.trim().toLowerCase();
        for (Map.Entry<String, EmbeddingAdapter> entry : embeddingAdapterMap.entrySet()) {
            String adapterName = entry.getKey();
            String adapterKey = adapterName.replaceAll("EmbeddingAdapter$", "").toLowerCase();
            if (adapterName.equalsIgnoreCase(provider) || adapterName.equalsIgnoreCase(normalized) || adapterKey.equals(normalized)) {
                return entry.getValue();
            }
        }
        throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "不支持的 Embedding 提供商: " + provider +
                ", 已配置的提供商: " + embeddingAdapterMap.keySet());
    }

    private String getApiKey(String provider) {
        String adapterKey = provider.replaceAll("EmbeddingAdapter$", "").toLowerCase();
        return llmProviderConfigService.getApiKey(adapterKey);
    }

    private boolean isApiKeyConfigured(String apiKey) {
        return apiKey != null && !apiKey.isBlank();
    }

    public boolean isProviderAvailable(String provider) {
        for (Map.Entry<String, EmbeddingAdapter> entry : embeddingAdapterMap.entrySet()) {
            String adapterName = entry.getKey();
            String adapterKey = adapterName.replaceAll("EmbeddingAdapter$", "").toLowerCase();
            if (adapterName.equalsIgnoreCase(provider) || adapterKey.equals(provider.trim().toLowerCase())) {
                if (!entry.getValue().isAvailable()) {
                    providerAvailability.put(adapterName, false);
                    return false;
                }
                String apiKey = getApiKey(provider);
                if (isApiKeyConfigured(apiKey)) {
                    providerAvailability.put(adapterName, true);
                    return true;
                }
                return providerAvailability.getOrDefault(adapterName, false);
            }
        }
        return false;
    }

    public void refreshProviderConfig(String provider) {
        final String normalized = provider.trim().toLowerCase();
        String adapterNameTemp = null;
        for (Map.Entry<String, EmbeddingAdapter> entry : embeddingAdapterMap.entrySet()) {
            String key = entry.getKey().replaceAll("EmbeddingAdapter$", "").toLowerCase();
            if (key.equals(normalized) || entry.getKey().equalsIgnoreCase(provider)) {
                adapterNameTemp = entry.getKey();
                break;
            }
        }
        if (adapterNameTemp == null) {
            log.warn("未找到匹配的 Embedding 适配器: {}", provider);
            return;
        }
        final String adapterName = adapterNameTemp;

        embeddingModelCache.keySet().removeIf(key -> key.startsWith(normalized + ":") || key.startsWith(adapterName + ":"));
        providerAvailability.remove(adapterName);
        log.info("已刷新 Embedding 提供商 [{}] 的缓存", provider);

        String apiKey = getApiKey(provider);
        if (isApiKeyConfigured(apiKey)) {
            try {
                EmbeddingAdapter adapter = getAdapter(provider);
                if (!adapter.isAvailable()) {
                    providerAvailability.put(adapterName, false);
                    log.warn("⚠️ Embedding 提供商 [{}] 不支持 Embedding 能力，已标记为不可用", provider);
                    return;
                }
                EmbeddingModel testModel = adapter.createEmbeddingModel(apiKey, null);
                testModel.embed("test");
                providerAvailability.put(adapterName, true);
                log.info("✅ Embedding 提供商 [{}] 配置更新后验证通过", provider);
            } catch (Exception e) {
                providerAvailability.put(adapterName, false);
                log.warn("⚠️ Embedding 提供商 [{}] 配置更新后验证失败: {}", provider, e.getMessage());
            }
        }
    }
}