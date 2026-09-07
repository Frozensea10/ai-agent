package com.agent.chat.controller;

import com.agent.chat.dto.CreateSessionRequest;
import com.agent.chat.dto.SendMessageRequest;
import com.agent.chat.dto.SSEMessage;
import com.agent.chat.service.ChatBizService;
import com.agent.chat.vo.ChatMessageVO;
import com.agent.chat.vo.ChatSessionVO;
import com.agent.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

import static com.agent.common.constant.CommonConstants.HEADER_USER_ID;

/**
 * 聊天会话控制器
 * <p>
 * 提供会话管理（查询/创建/删除）、历史消息查询、
 * 非流式消息发送以及基于 SSE 的流式对话接口。
 * </p>
 */
@Tag(name = "会话管理", description = "聊天会话、消息发送与流式对话接口")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ChatController {

    /** 聊天业务服务：承接全部业务编排 */
    private final ChatBizService chatBizService;

    /**
     * 查询当前用户的所有聊天会话。
     *
     * @param userId 当前用户 ID（请求头携带）
     * @return 会话列表
     */
    @Operation(summary = "查询会话列表", description = "查询当前用户的所有聊天会话")
    @GetMapping("/sessions")
    public Result<List<ChatSessionVO>> listSessions(@RequestHeader(HEADER_USER_ID) Long userId) {
        return Result.success(chatBizService.listSessions(userId));
    }

    /**
     * 为当前用户创建新的聊天会话。
     *
     * @param request 创建会话请求参数（agentId、kbId、title）
     * @param userId  当前用户 ID
     * @return 创建后的会话信息
     */
    @Operation(summary = "创建会话", description = "为当前用户创建新的聊天会话")
    @PostMapping("/sessions")
    public Result<ChatSessionVO> createSession(
            @Valid @RequestBody CreateSessionRequest request,
            @RequestHeader(HEADER_USER_ID) Long userId) {
        return Result.success(chatBizService.createSession(userId, request));
    }

    /**
     * 根据会话 ID 删除会话，并清空对应的聊天记忆。
     *
     * @param sessionId 会话 ID
     * @param userId    当前用户 ID
     * @return 空结果
     */
    @Operation(summary = "删除会话", description = "根据会话 ID 删除会话并清空对应记忆")
    @DeleteMapping("/sessions/{sessionId}")
    public Result<Void> deleteSession(
            @PathVariable @NotBlank String sessionId,
            @RequestHeader(HEADER_USER_ID) Long userId) {
        chatBizService.deleteSession(sessionId, userId);
        return Result.success();
    }

    /**
     * 根据会话 ID 查询历史消息列表（校验会话归属当前用户）。
     *
     * @param sessionId 会话 ID
     * @param userId    当前用户 ID
     * @return 历史消息列表
     */
    @Operation(summary = "查询会话消息", description = "根据会话 ID 查询历史消息列表")
    @GetMapping("/sessions/{sessionId}/messages")
    public Result<List<ChatMessageVO>> getMessages(
            @PathVariable @NotBlank String sessionId,
            @RequestHeader(HEADER_USER_ID) Long userId) {
        return Result.success(chatBizService.getMessages(sessionId, userId));
    }

    /**
     * 向指定会话发送非流式消息，同步返回完整的 AI 回复。
     *
     * @param sessionId 会话 ID
     * @param request   消息请求（内容、agentId、可选的 kbCode/模型参数）
     * @param userId    当前用户 ID
     * @return AI 回复消息
     */
    @Operation(summary = "发送消息", description = "向指定会话发送非流式消息，返回 AI 回复")
    @PostMapping("/sessions/{sessionId}/messages")
    public Result<ChatMessageVO> sendMessage(
            @PathVariable @NotBlank String sessionId,
            @Valid @RequestBody SendMessageRequest request,
            @RequestHeader(HEADER_USER_ID) Long userId) {
        return Result.success(chatBizService.sendMessage(sessionId, request, userId));
    }

    /**
     * 流式对话接口：向指定会话发送消息，并以 SSE 流式返回 AI 回复。
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
        return chatBizService.streamChat(sessionId, content, agentId, kbCode, modelProvider, modelName, userId);
    }
}
