package com.agent.chat.memory;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMemoryProvider {

    private static final int DEFAULT_MAX_MESSAGES = 20;
    private static final long CACHE_TTL_MINUTES = 30;
    private static final long CLEANUP_INTERVAL_MINUTES = 5;

    private final ChatMemoryStore chatMemoryStore;
    private final ConcurrentHashMap<String, ChatMemoryEntry> memoryCache = new ConcurrentHashMap<>();
    private ScheduledExecutorService cleanupExecutor;

    private static class ChatMemoryEntry {
        final ChatMemory chatMemory;
        volatile long lastAccessTime;

        ChatMemoryEntry(ChatMemory chatMemory) {
            this.chatMemory = chatMemory;
            this.lastAccessTime = System.currentTimeMillis();
        }

        void touch() {
            this.lastAccessTime = System.currentTimeMillis();
        }
    }

    @PostConstruct
    public void init() {
        cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "chat-memory-cache-cleanup");
            t.setDaemon(true);
            return t;
        });
        cleanupExecutor.scheduleAtFixedRate(this::cleanupExpiredEntries,
                CLEANUP_INTERVAL_MINUTES, CLEANUP_INTERVAL_MINUTES, TimeUnit.MINUTES);
    }

    @PreDestroy
    public void destroy() {
        if (cleanupExecutor != null) {
            cleanupExecutor.shutdownNow();
        }
        memoryCache.clear();
    }

    private void cleanupExpiredEntries() {
        long now = System.currentTimeMillis();
        long ttlMillis = TimeUnit.MINUTES.toMillis(CACHE_TTL_MINUTES);
        memoryCache.entrySet().removeIf(entry -> {
            if (now - entry.getValue().lastAccessTime > ttlMillis) {
                log.debug("清理过期 ChatMemory 缓存: sessionId={}", entry.getKey());
                return true;
            }
            return false;
        });
    }

    public ChatMemory getMemory(String sessionId, String memoryType, Integer maxMessages) {
        ChatMemoryEntry entry = memoryCache.computeIfAbsent(sessionId, k ->
                new ChatMemoryEntry(MessageWindowChatMemory.builder()
                        .maxMessages(DEFAULT_MAX_MESSAGES)
                        .chatMemoryStore(chatMemoryStore)
                        .id(sessionId)
                        .build()));
        entry.touch();
        return entry.chatMemory;
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
        memoryCache.remove(sessionId);
        chatMemoryStore.deleteMessages(sessionId);
    }
}
