package com.agent.chat.service;

import com.agent.chat.entity.ChatMessage;
import com.agent.chat.mapper.ChatMessageMapper;
import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

import static com.agent.chat.service.MessageConstants.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private final ChatMessageMapper messageMapper;

    public ChatMessage saveUserMessage(String sessionId, String content) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setMessageId(MessageIdGenerator.generate());
        message.setRole(MessageRole.USER.getCode());
        message.setContent(content);
        message.setContentType(DEFAULT_CONTENT_TYPE);
        message.setStatus(MessageStatus.NORMAL.getCode());
        messageMapper.insert(message);
        return message;
    }

    public ChatMessage saveAssistantMessage(String sessionId, String content, String modelName, Integer tokensUsed) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setMessageId(MessageIdGenerator.generate());
        message.setRole(MessageRole.ASSISTANT.getCode());
        message.setContent(content);
        message.setContentType(DEFAULT_CONTENT_TYPE);
        message.setModelName(modelName);
        message.setTokensUsed(tokensUsed);
        message.setStatus(MessageStatus.NORMAL.getCode());
        messageMapper.insert(message);
        return message;
    }

    public List<ChatMessage> getMessageHistory(String sessionId) {
        return messageMapper.selectBySessionId(sessionId);
    }

    public void updateMessageStatus(String messageId, Integer status) {
        ChatMessage message = messageMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getMessageId, messageId)
        );
        if (message != null) {
            message.setStatus(status);
            messageMapper.updateById(message);
        }
    }
}
