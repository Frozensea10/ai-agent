package com.agent.chat.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMemoryProvider {

    private static final int DEFAULT_MAX_MESSAGES = 10;
    private static final String KEY_SEPARATOR = ":";

    private final ChatMemoryStore chatMemoryStore;
    private final ConcurrentHashMap<String, ChatMemory> memoryCache = new ConcurrentHashMap<>();

    public ChatMemory getMemory(String sessionId, String memoryType, Integer maxMessages) {
        String key = buildKey(sessionId, memoryType, maxMessages);
        return memoryCache.computeIfAbsent(key, k -> MessageWindowChatMemory.builder()
                .maxMessages(maxMessages != null ? maxMessages : DEFAULT_MAX_MESSAGES)
                .chatMemoryStore(chatMemoryStore)
                .build());
    }

    public void addUserMessage(String sessionId, String memoryType, Integer maxMessages, String content) {
        getMemory(sessionId, memoryType, maxMessages).add(UserMessage.from(content));
    }

    public void addAiMessage(String sessionId, String memoryType, Integer maxMessages, String content) {
        getMemory(sessionId, memoryType, maxMessages).add(AiMessage.from(content));
    }

    public List<ChatMessage> getMessages(String sessionId, String memoryType, Integer maxMessages) {
        return getMemory(sessionId, memoryType, maxMessages).messages();
    }

    public void clear(String sessionId) {
        memoryCache.entrySet().removeIf(entry -> entry.getKey().startsWith(sessionId + KEY_SEPARATOR));
        chatMemoryStore.deleteMessages(sessionId);
    }

    private String buildKey(String sessionId, String memoryType, Integer maxMessages) {
        return sessionId + KEY_SEPARATOR + memoryType + KEY_SEPARATOR + maxMessages;
    }
}
