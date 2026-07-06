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
import reactor.core.publisher.FluxSink;

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
                       String ragContext, String memoryType, Integer maxMessages,
                       ChatModel chatModel) {
        chatMemoryProvider.addUserMessage(sessionId, memoryType, maxMessages, message);
        List<ChatMessage> history = chatMemoryProvider.getMessages(sessionId, memoryType, maxMessages);

        String prompt = buildSystemPrompt(systemPrompt, ragContext);
        if (prompt != null && !prompt.isBlank()) {
            history.add(0, SystemMessage.from(prompt));
        }

        ChatResponse response = chatModel.chat(history);
        String content = response.aiMessage().text();
        if (content == null) {
            content = "";
        }

        String processedContent = processToolCalls(content);

        chatMemoryProvider.addAiMessage(sessionId, memoryType, maxMessages, processedContent);
        return processedContent;
    }

    public Flux<SSEMessage> streamChat(String sessionId, String message, String systemPrompt,
                                   String ragContext, String memoryType, Integer maxMessages,
                                   StreamingChatModel streamingModel,
                                   String modelName) {
        String messageId = MessageIdGenerator.generate();
        AtomicBoolean cancelled = new AtomicBoolean(false);

        return Flux.<SSEMessage>create(sink -> {
            StringBuilder fullResponse = new StringBuilder();

            try {
                sink.next(SSEMessage.start(messageId));

                chatMemoryProvider.addUserMessage(sessionId, memoryType, maxMessages, message);
                List<ChatMessage> history = chatMemoryProvider.getMessages(sessionId, memoryType, maxMessages);

                String prompt = buildSystemPrompt(systemPrompt, ragContext);
                if (prompt != null && !prompt.isBlank()) {
                    history.add(0, SystemMessage.from(prompt));
                }

                streamingModel.chat(history, new StreamingChatResponseHandler() {
                    @Override
                    public void onPartialResponse(String partialResponse) {
                        if (cancelled.get()) return;
                        fullResponse.append(partialResponse);
                        sink.next(SSEMessage.content(partialResponse));
                    }

                    @Override
                    public void onCompleteResponse(ChatResponse completeResponse) {
                        if (cancelled.get()) return;
                        try {
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

                            sink.next(SSEMessage.end(messageId, usage));
                        } catch (Exception e) {
                            log.error("处理完整响应失败", e);
                        } finally {
                            sink.complete();
                        }
                    }

                    @Override
                    public void onError(Throwable error) {
                        log.error("流式对话错误", error);
                        sink.error(error);
                    }
                });
            } catch (Exception e) {
                log.error("流式对话执行异常", e);
                sink.error(e);
            }
        }, FluxSink.OverflowStrategy.BUFFER)
                .doOnCancel(() -> {
                    cancelled.set(true);
                    log.info("流式对话被取消: sessionId={}", sessionId);
                })
                .onErrorResume(error -> {
                    log.error("流式对话异常", error);
                    return Flux.just(SSEMessage.error("流式对话失败: " + error.getMessage()));
                });
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

    private String processToolCallsInStream(String content, FluxSink<SSEMessage> sink) {
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
                sink.next(SSEMessage.toolCall(toolName, arguments));

                Map<String, Object> params = objectMapper.readValue(arguments, MAP_TYPE_REFERENCE);
                Result<ToolExecuteResult> toolResult = mcpFeignClient.executeTool(toolName, params);

                String toolResultStr = buildToolResultString(toolResult);

                sink.next(SSEMessage.toolResult(toolName, toolResultStr));

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

    private String buildSystemPrompt(String systemPrompt, String ragContext) {
        if ((systemPrompt == null || systemPrompt.isBlank()) && (ragContext == null || ragContext.isBlank())) {
            return null;
        }
        StringBuilder prompt = new StringBuilder();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            prompt.append(systemPrompt);
        }
        if (ragContext != null && !ragContext.isBlank()) {
            if (prompt.length() > 0) {
                prompt.append("\n\n");
            }
            prompt.append("以下是与用户问题相关的参考信息，请严格基于这些信息回答，禁止忽略或编造:\n\n").append(ragContext).append("\n\n");
            prompt.append("回答要求：\n");
            prompt.append("1. 你必须优先基于上述参考信息回答用户问题。\n");
            prompt.append("2. 如果参考信息足以回答，请直接给出答案。\n");
            prompt.append("3. 如果参考信息不足，请明确说明\"根据提供的参考信息无法回答该问题\"。\n");
            prompt.append("4. 不要回答\"没有收到文件\"或\"没有看到知识库\"，因为参考信息已经提供。");
        }
        return prompt.toString();
    }
}
