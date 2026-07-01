package com.agent.chat.controller;

import com.agent.chat.dto.CreateSessionRequest;
import com.agent.chat.dto.SendMessageRequest;
import com.agent.chat.entity.ChatMessage;
import com.agent.chat.entity.ChatSession;
import com.agent.chat.feign.AgentFeignClient;
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
    private final ChatMemoryProvider chatMemoryProvider;

    @GetMapping("/sessions")
    public Result<List<ChatSessionVO>> listSessions(@RequestHeader(HEADER_USER_ID) Long userId) {
        List<ChatSession> sessions = sessionService.listSessions(userId);
        return Result.success(sessions.stream().map(this::convertToSessionVO).collect(Collectors.toList()));
    }

    @PostMapping("/sessions")
    public Result<ChatSessionVO> createSession(
            @Valid @RequestBody CreateSessionRequest request,
            @RequestHeader(HEADER_USER_ID) Long userId) {
        ChatSession session = sessionService.createSession(userId, request.getAgentId(), request.getKbId(), request.getTitle());
        return Result.success(convertToSessionVO(session));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public Result<Void> deleteSession(
            @PathVariable @NotBlank String sessionId,
            @RequestHeader(HEADER_USER_ID) Long userId) {
        sessionService.deleteSession(sessionId, userId);
        chatMemoryProvider.clear(sessionId);
        return Result.success();
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public Result<List<ChatMessageVO>> getMessages(
            @PathVariable @NotBlank String sessionId,
            @RequestHeader(HEADER_USER_ID) Long userId) {
        sessionService.getSession(sessionId, userId);
        List<ChatMessage> messages = messageService.getMessageHistory(sessionId);
        return Result.success(messages.stream().map(this::convertToMessageVO).collect(Collectors.toList()));
    }

    @PostMapping("/sessions/{sessionId}/messages")
    public Result<ChatMessageVO> sendMessage(
            @PathVariable @NotBlank String sessionId,
            @Valid @RequestBody SendMessageRequest request,
            @RequestHeader(HEADER_USER_ID) Long userId) {
        sessionService.getSession(sessionId, userId);

        AgentConfigDTO agent = resolveAgent(request.getAgentId());

        messageService.saveUserMessage(sessionId, request.getContent());

        ChatModel chatModel = llmService.createChatModel(
                agent.getModelProvider(),
                agent.getModelName(),
                agent.getTemperature(),
                agent.getMaxTokens()
        );

        String response = chatLLMService.chat(
                sessionId,
                request.getContent(),
                agent.getSystemPrompt(),
                agent.getMemoryType(),
                agent.getMemoryMaxMessages(),
                chatModel
        );

        ChatMessage aiMessage = messageService.saveAssistantMessage(
                sessionId, response, agent.getModelName(), null
        );
        sessionService.updateMessageCount(sessionId);

        return Result.success(convertToMessageVO(aiMessage));
    }

    @GetMapping(value = "/sessions/{sessionId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(
            @PathVariable @NotBlank String sessionId,
            @RequestParam @NotBlank String content,
            @RequestParam @NotNull Long agentId,
            @RequestHeader(HEADER_USER_ID) Long userId) {
        sessionService.getSession(sessionId, userId);

        AgentConfigDTO agent = resolveAgent(agentId);

        messageService.saveUserMessage(sessionId, content);

        StreamingChatModel streamingModel = llmService.createStreamingModel(
                agent.getModelProvider(),
                agent.getModelName(),
                agent.getTemperature(),
                agent.getMaxTokens()
        );

        return chatLLMService.streamChat(
                sessionId,
                content,
                agent.getSystemPrompt(),
                agent.getMemoryType(),
                agent.getMemoryMaxMessages(),
                streamingModel,
                agent.getModelName()
        );
    }

    private AgentConfigDTO resolveAgent(Long agentId) {
        Result<AgentConfigDTO> agentResult = agentFeignClient.getAgent(agentId);
        if (!agentResult.isSuccess() || agentResult.getData() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "Agent 不存在或无法访问");
        }
        return agentResult.getData();
    }

    private ChatSessionVO convertToSessionVO(ChatSession session) {
        ChatSessionVO vo = new ChatSessionVO();
        vo.setId(session.getId());
        vo.setSessionId(session.getSessionId());
        vo.setAgentId(session.getAgentId());
        vo.setKbId(session.getKbId());
        vo.setSessionTitle(session.getSessionTitle());
        vo.setMessageCount(session.getMessageCount());
        vo.setStatus(session.getStatus());
        vo.setCreatedAt(session.getCreatedAt());
        vo.setUpdatedAt(session.getUpdatedAt());
        return vo;
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
