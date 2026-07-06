package com.agent.chat.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisChatMemoryStore implements ChatMemoryStore {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${chat.memory.redis.prefix:chat:memory:}")
    private String keyPrefix;

    @Value("${chat.memory.redis.expire-hours:24}")
    private long expireHours;

    private static final TypeReference<List<ChatMessageDTO>> CHAT_MESSAGE_DTO_LIST_TYPE = new TypeReference<>() {};

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String key = buildKey(memoryId);
        String json = redisTemplate.opsForValue().get(key);
        if (json == null || json.isEmpty()) {
            log.debug("Redis 中无消息, memoryId={}", memoryId);
            return new ArrayList<>();
        }
        try {
            List<ChatMessageDTO> dtos = objectMapper.readValue(json, CHAT_MESSAGE_DTO_LIST_TYPE);
            List<ChatMessage> result = dtos.stream().map(ChatMessageDTO::toLangChainMessage).collect(Collectors.toList());
            log.debug("从 Redis 读取消息成功, memoryId={}, 消息数={}", memoryId, result.size());
            return result;
        } catch (JsonProcessingException e) {
            log.error("反序列化聊天记录失败, memoryId={}", memoryId, e);
            return new ArrayList<>();
        }
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String key = buildKey(memoryId);
        try {
            List<ChatMessageDTO> dtos = messages.stream().map(ChatMessageDTO::fromLangChainMessage).collect(Collectors.toList());
            String json = objectMapper.writeValueAsString(dtos);
            log.debug("保存消息到 Redis, memoryId={}, 消息数={}", memoryId, dtos.size());
            redisTemplate.opsForValue().set(key, json, expireHours, TimeUnit.HOURS);
        } catch (JsonProcessingException e) {
            log.error("序列化聊天记录失败, memoryId={}", memoryId, e);
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        String key = buildKey(memoryId);
        redisTemplate.delete(key);
    }

    private String buildKey(Object memoryId) {
        return keyPrefix + memoryId;
    }
}
