package com.agent.core.llm.service;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;

import java.util.Map;

public interface LLMService {

    ChatModel createChatModel(String provider, String modelName, Double temperature, Integer maxTokens);

    StreamingChatModel createStreamingModel(String provider, String modelName, Double temperature, Integer maxTokens);

    /**
     * 预热对话模型：提前构建并缓存同步与流式模型实例
     *
     * @param provider    提供商名称
     * @param modelName   模型名称，为空时使用提供商默认模型
     * @param temperature 温度参数
     * @param maxTokens   最大 Token 数
     */
    void warmup(String provider, String modelName, Double temperature, Integer maxTokens);

    /**
     * 检查提供商是否配置存在且API Key有效
     */
    boolean isProviderAvailable(String provider);

    /**
     * 获取所有提供商的可用性状态
     */
    Map<String, Boolean> getAllProviderAvailability();

    /**
     * 刷新指定提供商的模型缓存（保存配置后调用）
     */
    void refreshProviderConfig(String provider);
}
