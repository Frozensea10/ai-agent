package com.agent.core.llm.service;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.core.llm.adapter.ModelAdapter;
import com.agent.core.service.LlmProviderConfigService;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static com.agent.core.llm.adapter.LlmAdapterConstants.DEFAULT_MAX_TOKENS;
import static com.agent.core.llm.adapter.LlmAdapterConstants.DEFAULT_TEMPERATURE;

@Slf4j
@Service
@RequiredArgsConstructor
public class LLMServiceImpl implements LLMService, ApplicationRunner {

    static {
        System.setProperty("langchain4j.http.clientBuilderFactory",
                "dev.langchain4j.http.client.jdk.JdkHttpClientBuilderFactory");
    }

    private static final double HEALTH_CHECK_TEMPERATURE = 0.1;
    private static final int HEALTH_CHECK_MAX_TOKENS = 1;
    private static final String CACHE_KEY_FORMAT = "%s:%s:%s:%s";

    private final Map<String, ModelAdapter> modelAdapterMap;
    private final LlmProviderConfigService llmProviderConfigService;
    private final Map<String, ChatModel> chatModelCache = new ConcurrentHashMap<>();
    private final Map<String, StreamingChatModel> streamingModelCache = new ConcurrentHashMap<>();

    // 记录各提供商的可用性状态
    private final Map<String, Boolean> providerAvailability = new ConcurrentHashMap<>();

    @Override
    public void run(ApplicationArguments args) {
        System.setProperty("langchain4j.http.clientBuilderFactory",
                "dev.langchain4j.http.client.jdk.JdkHttpClientBuilderFactory");
        // 异步执行各提供商可用性检测，避免阻塞应用启动
        CompletableFuture.runAsync(() -> {
            log.info("LLM 模型可用性检测开始...");
            for (Map.Entry<String, ModelAdapter> entry : modelAdapterMap.entrySet()) {
                String provider = entry.getKey();
                ModelAdapter adapter = entry.getValue();
                String apiKey = getApiKey(provider);
                if (!isApiKeyConfigured(apiKey)) {
                    providerAvailability.put(provider, false);
                    log.warn("⚠️ 模型提供商 [{}] API key 未配置，跳过网络可用性检测", provider);
                    continue;
                }
                try {
                    ChatModel testModel = adapter.createChatModel(apiKey, null, HEALTH_CHECK_TEMPERATURE, HEALTH_CHECK_MAX_TOKENS);
                    String response = testModel.chat("Hello");
                    providerAvailability.put(provider, true);
                    log.info("✅ 模型提供商 [{}] 可用", provider);
                } catch (Exception e) {
                    providerAvailability.put(provider, false);
                    log.warn("⚠️ 模型提供商 [{}] 不可用: {}", provider, e.getMessage());
                }
            }
            log.info("LLM 模型可用性检测完成");
        });
    }

    @Override
    public ChatModel createChatModel(String provider, String modelName, Double temperature, Integer maxTokens) {
        validateProvider(provider);
        String cacheKey = buildCacheKey(provider, modelName, temperature, maxTokens);
        return chatModelCache.computeIfAbsent(cacheKey, k -> {
            ModelAdapter adapter = getAdapter(provider);
            return adapter.createChatModel(getApiKey(provider), modelName, temperature, maxTokens);
        });
    }

    @Override
    public StreamingChatModel createStreamingModel(String provider, String modelName, Double temperature, Integer maxTokens) {
        validateProvider(provider);
        String cacheKey = buildCacheKey(provider, modelName, temperature, maxTokens);
        return streamingModelCache.computeIfAbsent(cacheKey, k -> {
            ModelAdapter adapter = getAdapter(provider);
            return adapter.createStreamingModel(getApiKey(provider), modelName, temperature, maxTokens);
        });
    }

    @Override
    public void warmup(String provider, String modelName, Double temperature, Integer maxTokens) {
        createChatModel(provider, modelName, temperature, maxTokens);
        createStreamingModel(provider, modelName, temperature, maxTokens);
        log.info("预热模型完成，provider: {}, model: {}", provider, modelName);
    }

    private void validateProvider(String provider) {
        if (!isProviderAvailable(provider)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR.getCode(),
                    "模型提供商 [" + provider + "] 未配置或不可用，请检查 llm.xxx.api-key 配置");
        }
    }

    private String buildCacheKey(String provider, String modelName, Double temperature, Integer maxTokens) {
        return String.format(CACHE_KEY_FORMAT, provider, modelName, temperature, maxTokens);
    }

    private ModelAdapter getAdapter(String provider) {
        if (provider == null || provider.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "模型提供商不能为空");
        }
        String normalized = provider.trim().toLowerCase();
        for (Map.Entry<String, ModelAdapter> entry : modelAdapterMap.entrySet()) {
            String adapterName = entry.getKey();
            String adapterKey = adapterName.replaceAll("Adapter$", "").toLowerCase();
            if (adapterName.equalsIgnoreCase(provider) || adapterName.equalsIgnoreCase(normalized) || adapterKey.equals(normalized)) {
                return entry.getValue();
            }
        }
        throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "不支持的模型提供商: " + provider +
            ", 已配置的提供商: " + modelAdapterMap.keySet());
    }

    private String getApiKey(String provider) {
        String adapterKey = provider.replaceAll("Adapter$", "").toLowerCase();
        return llmProviderConfigService.getApiKey(adapterKey);
    }

    private boolean isApiKeyConfigured(String apiKey) {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public boolean isProviderAvailable(String provider) {
        if (provider == null || provider.isBlank()) {
            return false;
        }
        for (Map.Entry<String, ModelAdapter> entry : modelAdapterMap.entrySet()) {
            String adapterName = entry.getKey();
            String adapterKey = adapterName.replaceAll("Adapter$", "").toLowerCase();
            if (adapterName.equalsIgnoreCase(provider) || adapterKey.equals(provider.trim().toLowerCase())) {
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

    @Override
    public Map<String, Boolean> getAllProviderAvailability() {
        return new ConcurrentHashMap<>(providerAvailability);
    }

    @Override
    public void refreshProviderConfig(String provider) {
        final String normalized = provider.trim().toLowerCase();
        String adapterNameTemp = null;
        for (Map.Entry<String, ModelAdapter> entry : modelAdapterMap.entrySet()) {
            String key = entry.getKey().replaceAll("Adapter$", "").toLowerCase();
            if (key.equals(normalized) || entry.getKey().equalsIgnoreCase(provider)) {
                adapterNameTemp = entry.getKey();
                break;
            }
        }
        if (adapterNameTemp == null) {
            log.warn("未找到匹配的模型适配器: {}", provider);
            return;
        }
        final String adapterName = adapterNameTemp;

        chatModelCache.keySet().removeIf(key -> key.startsWith(normalized + ":") || key.startsWith(adapterName + ":"));
        streamingModelCache.keySet().removeIf(key -> key.startsWith(normalized + ":") || key.startsWith(adapterName + ":"));
        providerAvailability.remove(adapterName);
        log.info("已刷新模型提供商 [{}] 的缓存", provider);

        String apiKey = getApiKey(provider);
        if (isApiKeyConfigured(apiKey)) {
            try {
                ModelAdapter adapter = getAdapter(provider);
                ChatModel testModel = adapter.createChatModel(apiKey, null, HEALTH_CHECK_TEMPERATURE, HEALTH_CHECK_MAX_TOKENS);
                testModel.chat("Hello");
                providerAvailability.put(adapterName, true);
                log.info("✅ 模型提供商 [{}] 配置更新后验证通过", provider);
            } catch (Exception e) {
                providerAvailability.put(adapterName, false);
                log.warn("⚠️ 模型提供商 [{}] 配置更新后验证失败: {}", provider, e.getMessage());
            }
        }
    }
}
