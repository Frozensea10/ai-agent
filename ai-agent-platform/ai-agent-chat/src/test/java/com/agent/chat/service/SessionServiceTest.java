package com.agent.chat.service;

import com.agent.chat.entity.ChatSession;
import com.agent.chat.mapper.ChatSessionMapper;
import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock
    private ChatSessionMapper sessionMapper;

    @InjectMocks
    private SessionServiceImpl sessionService;

    @Test
    @DisplayName("创建会话成功并填充默认值")
    void shouldCreateSessionWithDefaults() {
        when(sessionMapper.insert(org.mockito.ArgumentMatchers.<ChatSession>any())).thenReturn(1);

        ChatSession session = sessionService.createSession(1L, 2L, 3L, "测试会话");

        assertNotNull(session);
        assertEquals(1L, session.getUserId());
        assertEquals(2L, session.getAgentId());
        assertEquals(3L, session.getKbId());
        assertEquals("测试会话", session.getSessionTitle());
        assertEquals(0, session.getMessageCount());
        assertEquals(1, session.getStatus());
        assertNotNull(session.getSessionId());
        assertEquals(32, session.getSessionId().length());
    }

    @Test
    @DisplayName("创建会话时标题为空则使用默认标题")
    void shouldCreateSessionWithDefaultTitle() {
        when(sessionMapper.insert(org.mockito.ArgumentMatchers.<ChatSession>any())).thenReturn(1);

        ChatSession session = sessionService.createSession(1L, 2L, null, null);

        assertEquals("新对话", session.getSessionTitle());
    }

    @Test
    @DisplayName("查询用户会话列表成功")
    void shouldListSessions() {
        ChatSession session = new ChatSession();
        session.setSessionId("abc123");
        session.setUserId(1L);
        when(sessionMapper.selectByUserId(1L)).thenReturn(List.of(session));

        List<ChatSession> sessions = sessionService.listSessions(1L);

        assertEquals(1, sessions.size());
        assertEquals("abc123", sessions.get(0).getSessionId());
    }

    @Test
    @DisplayName("根据会话 ID 查询成功")
    void shouldGetSession() {
        ChatSession session = new ChatSession();
        session.setId(1L);
        session.setSessionId("abc123");
        session.setUserId(1L);
        when(sessionMapper.selectBySessionId("abc123")).thenReturn(session);

        ChatSession result = sessionService.getSession("abc123", 1L);

        assertEquals("abc123", result.getSessionId());
        assertEquals(1L, result.getUserId());
    }

    @Test
    @DisplayName("查询不存在的会话抛出 NOT_FOUND")
    void shouldThrowWhenSessionNotFound() {
        when(sessionMapper.selectBySessionId("not_exist")).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> sessionService.getSession("not_exist", 1L));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("无权访问他人会话抛出 FORBIDDEN")
    void shouldThrowWhenAccessDenied() {
        ChatSession session = new ChatSession();
        session.setId(1L);
        session.setSessionId("abc123");
        session.setUserId(1L);
        when(sessionMapper.selectBySessionId("abc123")).thenReturn(session);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> sessionService.getSession("abc123", 2L));
        assertEquals(ErrorCode.FORBIDDEN.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("删除会话成功")
    void shouldDeleteSession() {
        ChatSession session = new ChatSession();
        session.setId(1L);
        session.setSessionId("abc123");
        session.setUserId(1L);
        when(sessionMapper.selectBySessionId("abc123")).thenReturn(session);

        sessionService.deleteSession("abc123", 1L);

        verify(sessionMapper).deleteById(1L);
    }

    @Test
    @DisplayName("更新会话消息计数")
    void shouldUpdateMessageCount() {
        sessionService.updateMessageCount("abc123");
        verify(sessionMapper).incrementMessageCount("abc123");
    }
}
