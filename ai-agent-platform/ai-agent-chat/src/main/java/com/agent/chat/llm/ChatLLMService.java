package com.agent.chat.llm;

import com.agent.chat.dto.SSEMessage;
import com.agent.chat.feign.McpFeignClient;
import com.agent.chat.memory.ChatMemoryProvider;
import com.agent.chat.service.MessageIdGenerator;
import com.agent.chat.service.MessageService;
import com.agent.common.result.Result;
import com.agent.mcp.dto.ToolExecuteResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.agent.chat.llm.ChatLLMConstants.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatLLMService {

    private final ChatMemoryProvider chatMemoryProvider;
    private final MessageService messageService;
    private final McpFeignClient mcpFeignClient;
    private final ObjectMapper objectMapper;

    private static final Pattern TOOL_CALL_PATTERN = Pattern.compile(TOOL_CALL_REGEX);
    private static final TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE = new TypeReference<>() {};

    public String chat(String sessionId, String message, String systemPrompt,
                       String memoryType, Integer maxMessages,
                       ChatModel chatModel) {
        chatMemoryProvider.addUserMessage(sessionId, memoryType, maxMessages, message);
        List<ChatMessage> history = chatMemoryProvider.getMessages(sessionId, memoryType, maxMessages);

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            history.add(0, SystemMessage.from(systemPrompt));
        }

        ChatResponse response = chatModel.chat(history);
        String content = response.aiMessage().text();

        String processedContent = processToolCalls(content);

        chatMemoryProvider.addAiMessage(sessionId, memoryType, maxMessages, processedContent);
        return processedContent;
    }

    public Flux<String> streamChat(String sessionId, String message, String systemPrompt,
                                   String memoryType, Integer maxMessages,
                                   StreamingChatModel streamingModel,
                                   String modelName) {
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        AtomicBoolean cancelled = new AtomicBoolean(false);
        String messageId = MessageIdGenerator.generate();
        StringBuilder fullResponse = new StringBuilder();

        try {
            sink.tryEmitNext(toJson(SSEMessage.start(messageId)));
        } catch (Exception e) {
            log.error("发送 start 消息失败", e);
        }

        chatMemoryProvider.addUserMessage(sessionId, memoryType, maxMessages, message);
        List<ChatMessage> history = chatMemoryProvider.getMessages(sessionId, memoryType, maxMessages);

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            history.add(0, SystemMessage.from(systemPrompt));
        }

        streamingModel.chat(history, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                if (cancelled.get()) return;
                fullResponse.append(partialResponse);
                try {
                    sink.tryEmitNext(toJson(SSEMessage.content(partialResponse)));
                } catch (Exception e) {
                    log.error("发送 content 消息失败", e);
                }
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                if (cancelled.get()) return;
                String content = completeResponse.aiMessage().text();

                String processedContent = processToolCallsInStream(content, sink);
                if (processedContent == null) {
                    processedContent = content;
                }

                chatMemoryProvider.addAiMessage(sessionId, memoryType, maxMessages, processedContent);

                Integer promptTokens = completeResponse.tokenUsage() != null ? completeResponse.tokenUsage().inputTokenCount() : null;
                Integer completionTokens = completeResponse.tokenUsage() != null ? completeResponse.tokenUsage().outputTokenCount() : null;
                Integer totalTokens = completeResponse.tokenUsage() != null ? completeResponse.tokenUsage().totalTokenCount() : null;

                SSEMessage.Usage usage = new SSEMessage.Usage();
                usage.setPromptTokens(promptTokens);
                usage.setCompletionTokens(completionTokens);
                usage.setTotalTokens(totalTokens);

                messageService.saveAssistantMessage(sessionId, processedContent, modelName, totalTokens);

                try {
                    sink.tryEmitNext(toJson(SSEMessage.end(messageId, usage)));
                    sink.tryEmitComplete();
                } catch (Exception e) {
                    log.error("发送 end 消息失败", e);
                    sink.tryEmitError(e);
                }
            }

            @Override
            public void onError(Throwable error) {
                log.error("流式对话错误", error);
                emitErrorAndComplete(sink, error.getMessage());
            }
        });

        return sink.asFlux()
                .doOnCancel(() -> {
                    cancelled.set(true);
                    log.info("流式对话被取消: sessionId={}", sessionId);
                })
                .onErrorResume(error -> {
                    log.error("流式对话异常", error);
                    return Flux.just(toJson(SSEMessage.error("流式对话失败: " + error.getMessage())));
                });
    }

    private void emitErrorAndComplete(Sinks.Many<String> sink, String message) {
        try {
            sink.tryEmitNext(toJson(SSEMessage.error(message)));
            sink.tryEmitComplete();
        } catch (Exception e) {
            log.error("发送 error 消息失败", e);
            sink.tryEmitError(e);
        }
    }

    private String processToolCalls(String content) {
        Matcher matcher = TOOL_CALL_PATTERN.matcher(content);
        StringBuilder result = new StringBuilder(content);

        while (matcher.find()) {
            String toolName = matcher.group(1);
            String arguments = matcher.group(2);

            log.info("检测到工具调用: toolName={}, arguments={}", toolName, arguments);

            try {
                Map<String, Object> params = objectMapper.readValue(arguments, MAP_TYPE_REFERENCE);
                Result<ToolExecuteResult> toolResult = mcpFeignClient.executeTool(toolName, params);

                String replacement = buildToolResultReplacement(toolResult);

                result.replace(matcher.start(), matcher.end(), replacement);
                matcher = TOOL_CALL_PATTERN.matcher(result.toString());
            } catch (Exception e) {
                log.error("工具调用处理失败", e);
            }
        }

        return result.toString();
    }

    private String processToolCallsInStream(String content, Sinks.Many<String> sink) {
        Matcher matcher = TOOL_CALL_PATTERN.matcher(content);
        if (!matcher.find()) {
            return null;
        }

        StringBuilder result = new StringBuilder(content);
        boolean hasToolCall = false;

        matcher.reset();
        while (matcher.find()) {
            String toolName = matcher.group(1);
            String arguments = matcher.group(2);
            hasToolCall = true;

            log.info("流式对话中检测到工具调用: toolName={}, arguments={}", toolName, arguments);

            try {
                sink.tryEmitNext(toJson(SSEMessage.toolCall(toolName, arguments)));

                Map<String, Object> params = objectMapper.readValue(arguments, MAP_TYPE_REFERENCE);
                Result<ToolExecuteResult> toolResult = mcpFeignClient.executeTool(toolName, params);

                String toolResultStr = buildToolResultString(toolResult);

                sink.tryEmitNext(toJson(SSEMessage.toolResult(toolName, toolResultStr)));

                String replacement = "\n[工具执行结果]\n" + toolResultStr + "\n";
                result.replace(matcher.start(), matcher.end(), replacement);
                matcher = TOOL_CALL_PATTERN.matcher(result.toString());
            } catch (Exception e) {
                log.error("流式工具调用处理失败", e);
            }
        }

        return hasToolCall ? result.toString() : null;
    }

    private String buildToolResultReplacement(Result<ToolExecuteResult> toolResult) {
        if (toolResult != null && toolResult.isSuccess() && toolResult.getData() != null && toolResult.getData().isSuccess()) {
            return "\n[工具执行结果]\n" + toolResult.getData().getData() + "\n";
        }
        String errorMessage = toolResult != null && toolResult.getData() != null
                ? toolResult.getData().getErrorMessage()
                : "未知错误";
        return "\n[工具执行失败]\n" + errorMessage + "\n";
    }

    private String buildToolResultString(Result<ToolExecuteResult> toolResult) throws Exception {
        if (toolResult != null && toolResult.isSuccess() && toolResult.getData() != null && toolResult.getData().isSuccess()) {
            return objectMapper.writeValueAsString(toolResult.getData().getData());
        }
        String errorMessage = toolResult != null && toolResult.getData() != null
                ? toolResult.getData().getErrorMessage()
                : "未知错误";
        return "{\"error\":\"" + errorMessage + "\"}";
    }

    private String toJson(SSEMessage message) {
        try {
            return "data: " + objectMapper.writeValueAsString(message) + "\n\n";
        } catch (Exception e) {
            log.error("序列化 SSEMessage 失败", e);
            return "data: {}\n\n";
        }
    }
}
