package com.agent.chat.service;

import com.agent.chat.entity.ChatSession;

import java.util.List;

public interface SessionService {

    ChatSession createSession(Long userId, Long agentId, Long kbId, String title);

    List<ChatSession> listSessions(Long userId);

    ChatSession getSession(String sessionId, Long userId);

    void deleteSession(String sessionId, Long userId);

    void updateMessageCount(String sessionId);
}
