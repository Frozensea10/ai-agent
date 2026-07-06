package com.agent.chat.controller;

import com.agent.chat.dto.CreateSessionRequest;
import com.agent.chat.dto.SendMessageRequest;
import com.agent.chat.dto.SSEMessage;
import com.agent.chat.entity.ChatMessage;
import com.agent.chat.entity.ChatSession;
import com.agent.chat.feign.AgentFeignClient;
import com.agent.chat.feign.KnowledgeFeignClient;
import com.agent.chat.llm.ChatLLMService;
import com.agent.chat.memory.ChatMemoryProvider;
import com.agent.chat.service.MessageService;
import com.agent.chat.service.SessionService;
import com.agent.chat.vo.ChatMessageVO;
import com.agent.chat.vo.ChatSessionVO;
import com.agent.common.dto.AgentConfigDTO;
import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.common.result.Result;
import com.agent.core.llm.service.LLMService;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;

import static com.agent.chat.controller.ChatControllerConstants.HEADER_USER_ID;

@Tag(name = "会话管理", description = "聊天会话、消息发送与流式对话接口")
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ChatController {

    private final SessionService sessionService;
    private final MessageService messageService;
    private final ChatLLMService chatLLMService;
    private final LLMService llmService;
    private final AgentFeignClient agentFeignClient;
    private final KnowledgeFeignClient knowledgeFeignClient;
    private final ChatMemoryProvider chatMemoryProvider;

    @Operation(summary = "查询会话列表", description = "查询当前用户的所有聊天会话")
    @GetMapping("/sessions")
    public Result<List<ChatSessionVO>> listSessions(@RequestHeader(HEADER_USER_ID) Long userId) {
        List<ChatSession> sessions = sessionService.listSessions(userId);
        return Result.success(sessions.stream().map(s -> convertToSessionVO(s, userId)).collect(Collectors.toList()));
    }

    @Operation(summary = "创建会话", description = "为当前用户创建新的聊天会话")
    @PostMapping("/sessions")
    public Result<ChatSessionVO> createSession(
            @Valid @RequestBody CreateSessionRequest request,
            @RequestHeader(HEADER_USER_ID) Long userId) {
        ChatSession session = sessionService.createSession(userId, request.getAgentId(), request.getKbId(), request.getTitle());
        return Result.success(convertToSessionVO(session, userId));
    }

    @Operation(summary = "删除会话", description = "根据会话 ID 删除会话并清空对应记忆")
    @DeleteMapping("/sessions/{sessionId}")
    public Result<Void> deleteSession(
            @PathVariable @NotBlank String sessionId,
            @RequestHeader(HEADER_USER_ID) Long userId) {
        sessionService.deleteSession(sessionId, userId);
        chatMemoryProvider.clear(sessionId);
        return Result.success();
    }

    @Operation(summary = "查询会话消息", description = "根据会话 ID 查询历史消息列表")
    @GetMapping("/sessions/{sessionId}/messages")
    public Result<List<ChatMessageVO>> getMessages(
            @PathVariable @NotBlank String sessionId,
            @RequestHeader(HEADER_USER_ID) Long userId) {
        sessionService.getSession(sessionId, userId);
        List<ChatMessage> messages = messageService.getMessageHistory(sessionId);
        return Result.success(messages.stream().map(this::convertToMessageVO).collect(Collectors.toList()));
    }

    @Operation(summary = "发送消息", description = "向指定会话发送非流式消息，返回 AI 回复")
    @PostMapping("/sessions/{sessionId}/messages")
    public Result<ChatMessageVO> sendMessage(
            @PathVariable @NotBlank String sessionId,
            @Valid @RequestBody SendMessageRequest request,
            @RequestHeader(HEADER_USER_ID) Long userId) {
        sessionService.getSession(sessionId, userId);

        AgentConfigDTO agent = resolveAgent(request.getAgentId(), userId);
        String kbCode = request.getKbCode();
        String ragContext = retrieveRagContext(kbCode, request.getContent(), userId);

        messageService.saveUserMessage(sessionId, request.getContent());

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
                chatModel
        );

        ChatMessage aiMessage = messageService.saveAssistantMessage(
                sessionId, response, modelName, null
        );
        sessionService.updateMessageCount(sessionId);

        return Result.success(convertToMessageVO(aiMessage));
    }

    @Operation(summary = "流式对话", description = "向指定会话发送消息并以 SSE 流式返回 AI 回复")
    @GetMapping(value = "/sessions/{sessionId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<SSEMessage> streamChat(
            @PathVariable @NotBlank String sessionId,
            @RequestParam @NotBlank String content,
            @RequestParam @NotNull Long agentId,
            @RequestParam(required = false) String kbCode,
            @RequestParam(required = false) String modelProvider,
            @RequestParam(required = false) String modelName,
            @RequestHeader(HEADER_USER_ID) Long userId) {
        log.info("流式对话请求 - sessionId: {}, agentId: {}, content: '{}', kbCode: {}, modelProvider: {}, modelName: {}", sessionId, agentId, content, kbCode, modelProvider, modelName);
        try {
            sessionService.getSession(sessionId, userId);

            AgentConfigDTO agent = resolveAgent(agentId, userId);
            String ragContext = retrieveRagContext(kbCode, content, userId);

            String provider = modelProvider != null && !modelProvider.isBlank() ? modelProvider : agent.getModelProvider();
            String name = modelName != null && !modelName.isBlank() ? modelName : agent.getModelName();

            StreamingChatModel streamingModel = llmService.createStreamingModel(
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
                    name
            );
        } catch (BusinessException e) {
            return Flux.just(SSEMessage.error(e.getMessage()));
        } catch (Exception e) {
            log.error("流式对话异常", e);
            return Flux.just(SSEMessage.error("流式对话失败: " + e.getMessage()));
        }
    }

    private AgentConfigDTO resolveAgent(Long agentId, Long userId) {
        Result<AgentConfigDTO> agentResult = agentFeignClient.getAgent(agentId, userId);
        if (agentResult == null || !agentResult.isSuccess() || agentResult.getData() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "Agent 不存在或无法访问");
        }
        return agentResult.getData();
    }

    private String retrieveRagContext(String kbCode, String query, Long userId) {
        if (kbCode == null || kbCode.isBlank()) {
            log.warn("RAG 未启用: kbCode 为空");
            return null;
        }
        try {
            log.info("RAG 检索开始: kbCode={}, query={}", kbCode, query);
            KnowledgeFeignClient.RAGQueryRequest request = new KnowledgeFeignClient.RAGQueryRequest();
            request.setQuery(query);
            request.setTopK(10);
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
