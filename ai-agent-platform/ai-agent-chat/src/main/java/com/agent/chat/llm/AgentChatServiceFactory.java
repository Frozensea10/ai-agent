package com.agent.chat.llm;

import com.agent.chat.memory.ChatMemoryProvider;
import com.agent.mcp.client.AgentToolProvider;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Agent 对话服务工厂
 * 负责创建配置好的 AgentChatService 实例，集成 MCP 工具
 */
@Slf4j
@Service
public class AgentChatServiceFactory {

    /** 默认最大记忆消息数 */
    private static final int DEFAULT_MAX_MESSAGES = 10;

    /** MCP 工具提供者 */
    private final AgentToolProvider agentToolProvider;
    /** 对话记忆提供者 */
    private final ChatMemoryProvider chatMemoryProvider;

    public AgentChatServiceFactory(AgentToolProvider agentToolProvider,
                                   ChatMemoryProvider chatMemoryProvider) {
        this.agentToolProvider = agentToolProvider;
        this.chatMemoryProvider = chatMemoryProvider;
    }

    /**
     * 创建支持工具调用的 AgentChatService
     *
     * @param chatModel   对话模型
     * @param sessionId   会话ID
     * @param memoryType  记忆类型
     * @param maxMessages 最大记忆消息数
     * @return 配置完成的 AgentChatService 实例
     */
    public AgentChatService create(ChatModel chatModel, String sessionId,
                                   String memoryType, Integer maxMessages) {
        ChatMemory chatMemory = buildChatMemory(memoryType, maxMessages);

        return AiServices.builder(AgentChatService.class)
                .chatModel(chatModel)
                .chatMemory(chatMemory)
                .toolProvider(agentToolProvider)
                .build();
    }

    /**
     * 创建支持工具调用的流式 AgentChatService
     *
     * @param streamingModel 流式对话模型
     * @param sessionId      会话ID
     * @param memoryType     记忆类型
     * @param maxMessages    最大记忆消息数
     * @return 配置完成的 AgentChatService 实例（支持流式 + 工具调用）
     */
    public AgentChatService createStreaming(StreamingChatModel streamingModel, String sessionId,
                                            String memoryType, Integer maxMessages) {
        ChatMemory chatMemory = buildChatMemory(memoryType, maxMessages);

        return AiServices.builder(AgentChatService.class)
                .streamingChatModel(streamingModel)
                .chatMemory(chatMemory)
                .toolProvider(agentToolProvider)
                .build();
    }

    /**
     * 构建对话记忆
     *
     * @param memoryType  记忆类型
     * @param maxMessages 最大记忆消息数
     * @return 对话记忆实例
     */
    private ChatMemory buildChatMemory(String memoryType, Integer maxMessages) {
        int max = maxMessages != null ? maxMessages : DEFAULT_MAX_MESSAGES;
        return MessageWindowChatMemory.withMaxMessages(max);
    }
}
