package com.agent.chat.service;

import com.agent.chat.entity.ChatSession;
import com.agent.chat.mapper.ChatSessionMapper;
import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionServiceImpl implements SessionService {

    private static final int STATUS_ACTIVE = 1;

    private final ChatSessionMapper sessionMapper;

    @Override
    public ChatSession createSession(Long userId, Long agentId, Long kbId, String title) {
        ChatSession session = new ChatSession();
        session.setSessionId(UUID.randomUUID().toString().replace("-", ""));
        session.setUserId(userId);
        session.setAgentId(agentId);
        session.setKbId(kbId);
        session.setSessionTitle(title != null ? title : "新对话");
        session.setMessageCount(0);
        session.setStatus(STATUS_ACTIVE);
        sessionMapper.insert(session);
        return session;
    }

    @Override
    public List<ChatSession> listSessions(Long userId) {
        return sessionMapper.selectByUserId(userId);
    }

    @Override
    public ChatSession getSession(String sessionId, Long userId) {
        ChatSession session = sessionMapper.selectBySessionId(sessionId);
        if (session == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "会话不存在");
        }
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "无权访问该会话");
        }
        return session;
    }

    @Override
    public void deleteSession(String sessionId, Long userId) {
        ChatSession session = getSession(sessionId, userId);
        sessionMapper.deleteById(session.getId());
    }

    @Override
    public void updateMessageCount(String sessionId) {
        sessionMapper.incrementMessageCount(sessionId);
    }
}
