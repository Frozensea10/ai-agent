package com.agent.core.llm.adapter;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LLM 适配器常量管理
 */
public final class LlmAdapterConstants {

    private LlmAdapterConstants() {
    }

    public static final double DEFAULT_TEMPERATURE = 0.7;
    public static final int DEFAULT_MAX_TOKENS = 2048;
    public static final long DEFAULT_TIMEOUT_SECONDS = 60L;
    public static final int DEFAULT_MAX_RETRIES = 3;

    public static final String PROVIDER_OPENAI = "openai";
    public static final String PROVIDER_DEEPSEEK = "deepseek";
    public static final String PROVIDER_QWEN = "qwen";
    public static final String PROVIDER_ANTHROPIC = "anthropic";

    public static final String MODEL_DEEPSEEK_CHAT = "deepseek-v4-flash";
    public static final String MODEL_DEEPSEEK_PRO = "deepseek-v4-pro";
    public static final String MODEL_QWEN_TURBO = "qwen-turbo";
    public static final String MODEL_CLAUDE_SONNET = "claude-3-sonnet-20240229";

    public static final String DEEPSEEK_BASE_URL = "https://api.deepseek.com/v1";

    public static final String OPENAI_EMBEDDING_URL = "https://api.openai.com/v1";
    public static final String DEEPSEEK_EMBEDDING_URL = "https://api.deepseek.com/v1";
    public static final String DEFAULT_EMBEDDING_MODEL = "text-embedding-3-small";
    // DeepSeek 官方不提供 Embedding API（/v1/embeddings 端点返回 404），
    // 因此不提供 DEEPSEEK_EMBEDDING_MODEL 常量。如需嵌入能力请使用 OpenAI 或其他支持的提供商。

    private static final Map<String, Float> TEMPERATURE_CACHE = new ConcurrentHashMap<>();

    public static float toFloatTemperature(Double temperature) {
        if (temperature == null) {
            return (float) DEFAULT_TEMPERATURE;
        }
        return TEMPERATURE_CACHE.computeIfAbsent(String.valueOf(temperature), k -> temperature.floatValue());
    }
}
