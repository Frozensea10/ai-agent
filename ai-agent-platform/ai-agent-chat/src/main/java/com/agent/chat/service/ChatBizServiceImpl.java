package com.agent.chat.service;

import com.agent.chat.dto.CreateSessionRequest;
import com.agent.chat.dto.SendMessageRequest;
import com.agent.chat.dto.SSEMessage;
import com.agent.chat.entity.ChatMessage;
import com.agent.chat.entity.ChatSession;
import com.agent.chat.feign.AgentFeignClient;
import com.agent.chat.feign.KnowledgeFeignClient;
import com.agent.chat.llm.ChatLLMConstants;
import com.agent.chat.llm.ChatLLMService;
import com.agent.chat.memory.ChatMemoryProvider;
import com.agent.chat.vo.ChatMessageVO;
import com.agent.chat.vo.ChatSessionVO;
import com.agent.common.dto.AgentConfigDTO;
import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.common.result.Result;
import com.agent.core.llm.service.LLMService;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 聊天业务编排服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatBizServiceImpl implements ChatBizService {

    /** RAG 检索默认返回的文档条数 */
    private static final int RAG_DEFAULT_TOP_K = 10;

    /** 会话服务：负责会话的增删查 */
    private final SessionService sessionService;
    /** 消息服务：负责消息持久化与历史查询 */
    private final MessageService messageService;
    /** 聊天 LLM 服务：封装对话与流式对话逻辑 */
    private final ChatLLMService chatLLMService;
    /** LLM 基础服务：用于创建 ChatModel / StreamingChatModel */
    private final LLMService llmService;
    /** Agent 服务远程调用客户端 */
    private final AgentFeignClient agentFeignClient;
    /** 知识库服务远程调用客户端 */
    private final KnowledgeFeignClient knowledgeFeignClient;
    /** 聊天记忆提供者：用于清理会话记忆 */
    private final ChatMemoryProvider chatMemoryProvider;

    @Override
    public List<ChatSessionVO> listSessions(Long userId) {
        List<ChatSession> sessions = sessionService.listSessions(userId);
        return sessions.stream().map(s -> convertToSessionVO(s, userId)).collect(Collectors.toList());
    }

    @Override
    public ChatSessionVO createSession(Long userId, CreateSessionRequest request) {
        ChatSession session = sessionService.createSession(userId, request.getAgentId(), request.getKbId(), request.getTitle());
        return convertToSessionVO(session, userId);
    }

    @Override
    public void deleteSession(String sessionId, Long userId) {
        sessionService.deleteSession(sessionId, userId);
        chatMemoryProvider.clear(sessionId);
    }

    @Override
    public List<ChatMessageVO> getMessages(String sessionId, Long userId) {
        sessionService.getSession(sessionId, userId);
        List<ChatMessage> messages = messageService.getMessageHistory(sessionId);
        return messages.stream().map(this::convertToMessageVO).collect(Collectors.toList());
    }

    /**
     * 向指定会话发送非流式消息，同步返回完整的 AI 回复。
     * <p>
     * 流程：校验会话 → 解析 Agent 配置 → RAG 检索（可选）→ 保存用户消息 →
     * 创建 ChatModel → 调用 LLM 生成回复 → 保存 AI 消息并更新会话消息数。
     * </p>
     */
    @Override
    public ChatMessageVO sendMessage(String sessionId, SendMessageRequest request, Long userId) {
        sessionService.getSession(sessionId, userId);

        // 解析 Agent 配置并执行 RAG 检索（若指定了知识库）
        AgentConfigDTO agent = resolveAgent(request.getAgentId(), userId);
        String kbCode = request.getKbCode();
        String ragContext = retrieveRagContext(kbCode, request.getContent(), userId);

        messageService.saveUserMessage(sessionId, request.getContent());

        // 请求中未指定模型时，使用 Agent 默认的模型配置
        String modelProvider = request.getModelProvider() != null && !request.getModelProvider().isBlank()
                ? request.getModelProvider() : agent.getModelProvider();
        String modelName = request.getModelName() != null && !request.getModelName().isBlank()
                ? request.getModelName() : agent.getModelName();

        ChatModel chatModel = llmService.createChatModel(
                modelProvider,
                modelName,
                agent.getTemperature(),
                agent.getMaxTokens()
        );

        String response = chatLLMService.chat(
                sessionId,
                request.getContent(),
                agent.getSystemPrompt(),
                ragContext,
                agent.getMemoryType(),
                agent.getMemoryMaxMessages(),
                chatModel,
                userId
        );

        ChatMessage aiMessage = messageService.saveAssistantMessage(
                sessionId, response, modelName, null
        );
        sessionService.updateMessageCount(sessionId);

        return convertToMessageVO(aiMessage);
    }

    /**
     * 流式对话：向指定会话发送消息，并以 SSE 流式返回 AI 回复。
     * <p>
     * 同时创建流式模型（用于回复生成）与普通模型（用于标题生成等场景）。
     * 业务异常与未知异常均会转换为 SSE 错误消息返回，而不是抛出 HTTP 错误。
     * </p>
     */
    @Override
    public Flux<SSEMessage> streamChat(String sessionId, String content, Long agentId, String kbCode, String modelProvider, String modelName, Long userId) {
        log.info("流式对话请求 - sessionId: {}, agentId: {}, content: '{}', kbCode: {}, modelProvider: {}, modelName: {}", sessionId, agentId, content, kbCode, modelProvider, modelName);
        try {
            sessionService.getSession(sessionId, userId);

            // 解析 Agent 配置并执行 RAG 检索（若指定了知识库）
            AgentConfigDTO agent = resolveAgent(agentId, userId);
            String ragContext = retrieveRagContext(kbCode, content, userId);

            // 未指定模型参数时回退到 Agent 默认配置
            String provider = modelProvider != null && !modelProvider.isBlank() ? modelProvider : agent.getModelProvider();
            String name = modelName != null && !modelName.isBlank() ? modelName : agent.getModelName();

            StreamingChatModel streamingModel = llmService.createStreamingModel(
                    provider,
                    name,
                    agent.getTemperature(),
                    agent.getMaxTokens()
            );

            ChatModel chatModel = llmService.createChatModel(
                    provider,
                    name,
                    agent.getTemperature(),
                    agent.getMaxTokens()
            );

            messageService.saveUserMessage(sessionId, content);

            return chatLLMService.streamChat(
                    sessionId,
                    content,
                    agent.getSystemPrompt(),
                    ragContext,
                    agent.getMemoryType(),
                    agent.getMemoryMaxMessages(),
                    streamingModel,
                    chatModel,
                    name,
                    userId
            );
        } catch (BusinessException e) {
            return Flux.just(SSEMessage.error(e.getMessage()));
        } catch (Exception e) {
            log.error("流式对话异常", e);
            return Flux.just(SSEMessage.error(ChatLLMConstants.STREAM_CHAT_ERROR_PREFIX + e.getMessage()));
        }
    }

    /**
     * 通过 Feign 调用 Agent 服务获取 Agent 配置，不存在或不可访问时抛出业务异常。
     *
     * @param agentId Agent ID
     * @param userId  当前用户 ID
     * @return Agent 配置
     */
    private AgentConfigDTO resolveAgent(Long agentId, Long userId) {
        Result<AgentConfigDTO> agentResult = agentFeignClient.getAgent(agentId, userId);
        if (agentResult == null || !agentResult.isSuccess() || agentResult.getData() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "Agent 不存在或无法访问");
        }
        return agentResult.getData();
    }

    /**
     * 执行 RAG 检索：根据知识库编码与用户查询获取上下文。
     * <p>
     * kbCode 为空或检索失败时返回 null，不影响主对话流程。
     * </p>
     *
     * @param kbCode 知识库编码（可为空）
     * @param query  用户查询内容
     * @param userId 当前用户 ID
     * @return RAG 上下文文本，未启用或失败时为 null
     */
    private String retrieveRagContext(String kbCode, String query, Long userId) {
        if (kbCode == null || kbCode.isBlank()) {
            log.warn("RAG 未启用: kbCode 为空");
            return null;
        }
        try {
            log.info("RAG 检索开始: kbCode={}, query={}", kbCode, query);
            KnowledgeFeignClient.RAGQueryRequest request = new KnowledgeFeignClient.RAGQueryRequest();
            request.setQuery(query);
            request.setTopK(RAG_DEFAULT_TOP_K);
            Result<KnowledgeFeignClient.RAGResponse> result = knowledgeFeignClient.ragQuery(kbCode, request, userId);
            log.info("RAG 检索响应: kbCode={}, success={}", kbCode, result != null && result.isSuccess());
            if (result != null && result.isSuccess() && result.getData() != null) {
                String context = result.getData().getContext();
                log.info("RAG 检索成功: kbCode={}, contextLength={}", kbCode, context != null ? context.length() : 0);
                return context;
            }
            log.warn("RAG 检索未返回有效结果: kbCode={}, result={}", kbCode, result);
        } catch (Exception e) {
            log.error("RAG 检索失败: kbCode={}, query={}", kbCode, query, e);
        }
        return null;
    }

    /**
     * 将会话实体转换为 VO，并附带知识库编码（kbCode）。
     *
     * @param session 会话实体
     * @param userId  当前用户 ID（用于查询知识库编码）
     * @return 会话 VO
     */
    private ChatSessionVO convertToSessionVO(ChatSession session, Long userId) {
        ChatSessionVO vo = new ChatSessionVO();
        vo.setId(session.getId());
        vo.setSessionId(session.getSessionId());
        vo.setAgentId(session.getAgentId());
        vo.setKbId(session.getKbId());
        vo.setKbCode(resolveKbCode(session.getKbId(), userId));
        vo.setSessionTitle(session.getSessionTitle());
        vo.setMessageCount(session.getMessageCount());
        vo.setStatus(session.getStatus());
        vo.setCreatedAt(session.getCreatedAt());
        vo.setUpdatedAt(session.getUpdatedAt());
        return vo;
    }

    /**
     * 根据知识库 ID 查询知识库编码，查询失败时返回 null 并记录警告日志。
     *
     * @param kbId   知识库 ID（可为空）
     * @param userId 当前用户 ID
     * @return 知识库编码，未绑定或查询失败时为 null
     */
    private String resolveKbCode(Long kbId, Long userId) {
        if (kbId == null) {
            return null;
        }
        try {
            Result<KnowledgeFeignClient.KnowledgeBaseVO> result = knowledgeFeignClient.getKnowledgeBaseById(kbId, userId);
            if (result != null && result.isSuccess() && result.getData() != null) {
                return result.getData().getKbCode();
            }
        } catch (Exception e) {
            log.warn("查询知识库编码失败: kbId={}", kbId, e);
        }
        return null;
    }

    /**
     * 将消息实体转换为 VO 返回给前端。
     *
     * @param message 消息实体
     * @return 消息 VO
     */
    private ChatMessageVO convertToMessageVO(ChatMessage message) {
        ChatMessageVO vo = new ChatMessageVO();
        vo.setId(message.getId());
        vo.setMessageId(message.getMessageId());
        vo.setRole(message.getRole());
        vo.setContent(message.getContent());
        vo.setContentType(message.getContentType());
        vo.setTokensUsed(message.getTokensUsed());
        vo.setModelName(message.getModelName());
        vo.setCreatedAt(message.getCreatedAt());
        return vo;
    }
}
