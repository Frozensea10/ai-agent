package com.agent.chat.service;

import com.agent.chat.dto.CreateSessionRequest;
import com.agent.chat.dto.SendMessageRequest;
import com.agent.chat.dto.SSEMessage;
import com.agent.chat.vo.ChatMessageVO;
import com.agent.chat.vo.ChatSessionVO;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 聊天业务编排服务
 * <p>
 * 承接 ChatController 的业务编排逻辑：会话管理、消息发送（非流式/流式）、
 * RAG 检索、Agent 配置解析与 VO 转换。
 * </p>
 */
public interface ChatBizService {

    /**
     * 查询当前用户的所有聊天会话，并转换为 VO 返回。
     *
     * @param userId 当前用户 ID
     * @return 会话列表
     */
    List<ChatSessionVO> listSessions(Long userId);

    /**
     * 为当前用户创建新的聊天会话。
     *
     * @param userId  当前用户 ID
     * @param request 创建会话请求参数（agentId、kbId、title）
     * @return 创建后的会话信息
     */
    ChatSessionVO createSession(Long userId, CreateSessionRequest request);

    /**
     * 根据会话 ID 删除会话，并清空对应的聊天记忆。
     *
     * @param sessionId 会话 ID
     * @param userId    当前用户 ID
     */
    void deleteSession(String sessionId, Long userId);

    /**
     * 根据会话 ID 查询历史消息列表（校验会话归属当前用户）。
     *
     * @param sessionId 会话 ID
     * @param userId    当前用户 ID
     * @return 历史消息列表
     */
    List<ChatMessageVO> getMessages(String sessionId, Long userId);

    /**
     * 向指定会话发送非流式消息，同步返回完整的 AI 回复。
     *
     * @param sessionId 会话 ID
     * @param request   消息请求（内容、agentId、可选的 kbCode/模型参数）
     * @param userId    当前用户 ID
     * @return AI 回复消息
     */
    ChatMessageVO sendMessage(String sessionId, SendMessageRequest request, Long userId);

    /**
     * 流式对话：向指定会话发送消息，并以 SSE 流式返回 AI 回复。
     *
     * @param sessionId     会话 ID
     * @param content       用户消息内容
     * @param agentId       Agent ID
     * @param kbCode        知识库编码（可选，启用 RAG）
     * @param modelProvider 模型提供方（可选，缺省使用 Agent 配置）
     * @param modelName     模型名称（可选，缺省使用 Agent 配置）
     * @param userId        当前用户 ID
     * @return SSE 消息流
     */
    Flux<SSEMessage> streamChat(String sessionId, String content, Long agentId, String kbCode, String modelProvider, String modelName, Long userId);
}
