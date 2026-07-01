package com.agent.core.llm.service;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.core.llm.adapter.ModelAdapter;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.agent.core.llm.adapter.LlmAdapterConstants.DEFAULT_MAX_TOKENS;
import static com.agent.core.llm.adapter.LlmAdapterConstants.DEFAULT_TEMPERATURE;

@Slf4j
@Service
@RequiredArgsConstructor
public class LLMService implements ApplicationRunner {

    static {
        System.setProperty("langchain4j.http.clientBuilderFactory",
                "dev.langchain4j.http.client.jdk.JdkHttpClientBuilderFactory");
    }

    private static final double HEALTH_CHECK_TEMPERATURE = 0.1;
    private static final int HEALTH_CHECK_MAX_TOKENS = 1;
    private static final String CACHE_KEY_FORMAT = "%s:%s:%s:%s";

    private final Map<String, ModelAdapter> modelAdapterMap;
    private final Map<String, ChatModel> chatModelCache = new ConcurrentHashMap<>();
    private final Map<String, StreamingChatModel> streamingModelCache = new ConcurrentHashMap<>();

    // 记录各提供商的可用性状态
    private final Map<String, Boolean> providerAvailability = new ConcurrentHashMap<>();

    @Override
    public void run(ApplicationArguments args) {
        System.setProperty("langchain4j.http.clientBuilderFactory",
                "dev.langchain4j.http.client.jdk.JdkHttpClientBuilderFactory");
        log.info("LLM 模型可用性检测开始...");
        for (Map.Entry<String, ModelAdapter> entry : modelAdapterMap.entrySet()) {
            String provider = entry.getKey();
            try {
                ChatModel testModel = entry.getValue().createChatModel(null, HEALTH_CHECK_TEMPERATURE, HEALTH_CHECK_MAX_TOKENS);
                String response = testModel.chat("Hello");
                providerAvailability.put(provider, true);
                log.info("✅ 模型提供商 [{}] 可用", provider);
            } catch (Exception e) {
                providerAvailability.put(provider, false);
                log.warn("⚠️ 模型提供商 [{}] 不可用: {}", provider, e.getMessage());
            }
        }
        log.info("LLM 模型可用性检测完成");
    }

    public ChatModel createChatModel(String provider, String modelName, Double temperature, Integer maxTokens) {
        String cacheKey = buildCacheKey(provider, modelName, temperature, maxTokens);
        return chatModelCache.computeIfAbsent(cacheKey, k -> {
            ModelAdapter adapter = getAdapter(provider);
            return adapter.createChatModel(modelName, temperature, maxTokens);
        });
    }

    public StreamingChatModel createStreamingModel(String provider, String modelName, Double temperature, Integer maxTokens) {
        String cacheKey = buildCacheKey(provider, modelName, temperature, maxTokens);
        return streamingModelCache.computeIfAbsent(cacheKey, k -> {
            ModelAdapter adapter = getAdapter(provider);
            return adapter.createStreamingModel(modelName, temperature, maxTokens);
        });
    }

    private String buildCacheKey(String provider, String modelName, Double temperature, Integer maxTokens) {
        return String.format(CACHE_KEY_FORMAT, provider, modelName, temperature, maxTokens);
    }

    private ModelAdapter getAdapter(String provider) {
        ModelAdapter adapter = modelAdapterMap.get(provider);
        if (adapter == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "不支持的模型提供商: " + provider +
                ", 已配置的提供商: " + modelAdapterMap.keySet());
        }
        return adapter;
    }

    /**
     * 检查提供商是否配置存在且API Key有效
     */
    public boolean isProviderAvailable(String provider) {
        return providerAvailability.getOrDefault(provider, false);
    }

    /**
     * 获取所有提供商的可用性状态
     */
    public Map<String, Boolean> getAllProviderAvailability() {
        return new ConcurrentHashMap<>(providerAvailability);
    }
}
