package com.agent.chat.service;

import com.agent.chat.entity.ChatMessage;

import java.util.List;

public interface MessageService {

    ChatMessage saveUserMessage(String sessionId, String content);

    ChatMessage saveAssistantMessage(String sessionId, String content, String modelName, Integer tokensUsed);

    List<ChatMessage> getMessageHistory(String sessionId);

    void updateMessageStatus(String messageId, Integer status);
}
