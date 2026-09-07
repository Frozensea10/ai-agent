package com.agent.chat.service;

import com.agent.chat.entity.ChatMessage;
import com.agent.chat.mapper.ChatMessageMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private ChatMessageMapper messageMapper;

    @InjectMocks
    private MessageServiceImpl messageService;

    @Test
    @DisplayName("保存用户消息成功")
    void shouldSaveUserMessage() {
        when(messageMapper.insert(ArgumentMatchers.<ChatMessage>any())).thenReturn(1);

        ChatMessage message = messageService.saveUserMessage("session123", "你好");

        assertNotNull(message);
        assertEquals("session123", message.getSessionId());
        assertEquals("user", message.getRole());
        assertEquals("你好", message.getContent());
        assertEquals("text", message.getContentType());
        assertEquals(1, message.getStatus());
        assertTrue(message.getMessageId().startsWith("msg_"));
    }

    @Test
    @DisplayName("保存助手消息成功")
    void shouldSaveAssistantMessage() {
        when(messageMapper.insert(ArgumentMatchers.<ChatMessage>any())).thenReturn(1);

        ChatMessage message = messageService.saveAssistantMessage("session123", "我很好", "gpt-4o-mini", 15);

        assertNotNull(message);
        assertEquals("assistant", message.getRole());
        assertEquals("我很好", message.getContent());
        assertEquals("gpt-4o-mini", message.getModelName());
        assertEquals(15, message.getTokensUsed());
        assertEquals("text", message.getContentType());
    }

    @Test
    @DisplayName("获取消息历史成功")
    void shouldGetMessageHistory() {
        ChatMessage message = new ChatMessage();
        message.setSessionId("session123");
        message.setRole("user");
        message.setContent("你好");

        when(messageMapper.selectBySessionId("session123")).thenReturn(List.of(message));

        List<ChatMessage> history = messageService.getMessageHistory("session123");

        assertEquals(1, history.size());
        assertEquals("你好", history.get(0).getContent());
    }

    @Test
    @DisplayName("更新消息状态成功")
    void shouldUpdateMessageStatus() {
        ChatMessage message = new ChatMessage();
        message.setId(1L);
        message.setMessageId("msg_abc123");
        message.setStatus(1);

        when(messageMapper.selectOne(ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChatMessage>>any()))
                .thenReturn(message);
        when(messageMapper.updateById(message)).thenReturn(1);

        messageService.updateMessageStatus("msg_abc123", 0);

        assertEquals(0, message.getStatus());
        verify(messageMapper).updateById(message);
    }

    @Test
    @DisplayName("更新不存在消息状态时不报错")
    void shouldNotThrowWhenUpdateNonExistentMessage() {
        when(messageMapper.selectOne(ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChatMessage>>any()))
                .thenReturn(null);

        messageService.updateMessageStatus("msg_not_exist", 0);

        verify(messageMapper).selectOne(ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChatMessage>>any());
    }
}
