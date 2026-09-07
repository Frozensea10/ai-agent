package com.agent.chat.llm;

import com.agent.chat.dto.SSEMessage;
import com.agent.chat.feign.McpFeignClient;
import com.agent.chat.memory.ChatMemoryProvider;
import com.agent.chat.service.MessageIdGenerator;
import com.agent.chat.service.MessageService;
import com.agent.chat.service.SessionService;
import com.agent.common.result.Result;
import com.agent.mcp.dto.ToolExecuteResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.agent.chat.llm.ChatLLMConstants.FUNCTION_CALL_REGEX;
import static com.agent.chat.llm.ChatLLMConstants.JSON_CODE_BLOCK_REGEX;
import static com.agent.chat.llm.ChatLLMConstants.PLAIN_TOOL_CALL_REGEX;
import static com.agent.chat.llm.ChatLLMConstants.TOOL_CALL_REGEX;
import static com.agent.chat.llm.ChatLLMConstants.TOOL_NAME_REGEX;

@Slf4j
@Service
public class ChatLLMServiceImpl implements ChatLLMService {

    private final ChatMemoryProvider chatMemoryProvider;
    private final MessageService messageService;
    private final SessionService sessionService;
    private final McpFeignClient mcpFeignClient;
    private final ObjectMapper objectMapper;
    private final AgentChatServiceFactory agentChatServiceFactory;

    private static final Pattern TOOL_CALL_PATTERN = Pattern.compile(TOOL_CALL_REGEX);
    private static final TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE = new TypeReference<>() {};

    public ChatLLMServiceImpl(ChatMemoryProvider chatMemoryProvider,
                              MessageService messageService,
                              SessionService sessionService,
                              McpFeignClient mcpFeignClient,
                              ObjectMapper objectMapper,
                              AgentChatServiceFactory agentChatServiceFactory) {
        this.chatMemoryProvider = chatMemoryProvider;
        this.messageService = messageService;
        this.sessionService = sessionService;
        this.mcpFeignClient = mcpFeignClient;
        this.objectMapper = objectMapper;
        this.agentChatServiceFactory = agentChatServiceFactory;
    }

    @Override
    public String chat(String sessionId, String message, String systemPrompt,
                       String ragContext, String memoryType, Integer maxMessages,
                       ChatModel chatModel, Long userId) {
        chatMemoryProvider.addUserMessage(sessionId, memoryType, maxMessages, message);

        // 通过 AgentChatServiceFactory 创建带工具调用的 Agent，由 LangChain4j 自动处理 function calling
        AgentChatService agent = agentChatServiceFactory.create(chatModel, sessionId, memoryType, maxMessages);

        String safeSystemPrompt = systemPrompt != null ? systemPrompt : "";
        String safeRagContext = ragContext != null ? ragContext : "";

        String response = agent.chat(message, safeSystemPrompt, safeRagContext);
        if (response == null) {
            response = "";
        }

        chatMemoryProvider.addAiMessage(sessionId, memoryType, maxMessages, response);
        return response;
    }

    @Override
    public Flux<SSEMessage> streamChat(String sessionId, String message, String systemPrompt,
                                   String ragContext, String memoryType, Integer maxMessages,
                                   StreamingChatModel streamingModel, ChatModel chatModel,
                                   String modelName, Long userId) {
        String messageId = MessageIdGenerator.generate();
        AtomicBoolean cancelled = new AtomicBoolean(false);

        return Flux.<SSEMessage>create(sink -> {
            StringBuilder fullResponse = new StringBuilder();

            try {
                sink.next(SSEMessage.start(messageId));

                chatMemoryProvider.addUserMessage(sessionId, memoryType, maxMessages, message);

                // 通过 AiServices 走流式 + 工具调用路径，工具执行完成后自动继续生成最终回复
                AgentChatService agent = agentChatServiceFactory.createStreaming(
                        streamingModel, sessionId, memoryType, maxMessages);

                String safeSystemPrompt = systemPrompt != null ? systemPrompt : "";
                String safeRagContext = ragContext != null ? ragContext : "";

                agent.streamChat(message, safeSystemPrompt, safeRagContext)
                        .onPartialResponse(partialResponse -> {
                            if (cancelled.get()) return;
                            fullResponse.append(partialResponse);
                            sink.next(SSEMessage.content(partialResponse));
                        })
                        .onCompleteResponse(completeResponse -> {
                            if (cancelled.get()) return;
                            try {
                                String content = completeResponse.aiMessage().text();
                                if (content == null) {
                                    content = "";
                                }

                                chatMemoryProvider.addAiMessage(sessionId, memoryType, maxMessages, content);

                                Integer promptTokens = completeResponse.tokenUsage() != null ? completeResponse.tokenUsage().inputTokenCount() : null;
                                Integer completionTokens = completeResponse.tokenUsage() != null ? completeResponse.tokenUsage().outputTokenCount() : null;
                                Integer totalTokens = completeResponse.tokenUsage() != null ? completeResponse.tokenUsage().totalTokenCount() : null;

                                SSEMessage.Usage usage = new SSEMessage.Usage();
                                usage.setPromptTokens(promptTokens);
                                usage.setCompletionTokens(completionTokens);
                                usage.setTotalTokens(totalTokens);

                                messageService.saveAssistantMessage(sessionId, content, modelName, totalTokens);
                                sessionService.updateMessageCount(sessionId);

                                sink.next(SSEMessage.end(messageId, usage));
                            } catch (Exception e) {
                                log.error("处理完整响应失败", e);
                            } finally {
                                sink.complete();
                            }
                        })
                        .onError(error -> {
                            log.error("流式对话错误", error);
                            sink.error(error);
                        })
                        .start();
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
                    return Flux.just(SSEMessage.error(ChatLLMConstants.STREAM_CHAT_ERROR_PREFIX + error.getMessage()));
                });
    }

    private List<ChatMessage> buildHistoryWithPrompt(String sessionId, String memoryType, Integer maxMessages, String prompt) {
        List<ChatMessage> history = new ArrayList<>(chatMemoryProvider.getMessages(sessionId, memoryType, maxMessages));
        if (prompt != null && !prompt.isBlank()) {
            history.add(0, SystemMessage.from(prompt));
        }
        return history;
    }

    private boolean hasToolCall(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        String normalized = normalizeToolCalls(content);
        return TOOL_CALL_PATTERN.matcher(normalized).find();
    }

    private String processToolCalls(String content, Long userId) {
        content = normalizeToolCalls(content);
        Matcher matcher = TOOL_CALL_PATTERN.matcher(content);
        StringBuilder result = new StringBuilder(content);

        while (matcher.find()) {
            String toolName = matcher.group(1);
            String arguments = matcher.group(2);

            log.info("检测到工具调用: toolName={}, arguments={}", toolName, arguments);

            try {
                Map<String, Object> params = objectMapper.readValue(arguments, MAP_TYPE_REFERENCE);
                Result<ToolExecuteResult> toolResult = mcpFeignClient.executeTool(toolName, params, userId);

                String replacement = buildToolResultReplacement(toolResult);

                result.replace(matcher.start(), matcher.end(), replacement);
                matcher = TOOL_CALL_PATTERN.matcher(result.toString());
            } catch (Exception e) {
                log.error("工具调用处理失败", e);
            }
        }

        return result.toString();
    }

    private String processToolCallsInStream(String content, FluxSink<SSEMessage> sink, Long userId) {
        content = normalizeToolCalls(content);
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
                Result<ToolExecuteResult> toolResult = mcpFeignClient.executeTool(toolName, params, userId);

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

    private String normalizeToolCalls(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }
        content = normalizeToolNameBlocks(content, FUNCTION_CALL_REGEX, "function_call");
        content = normalizeToolNameBlocks(content, TOOL_NAME_REGEX, "tool_name");
        content = normalizePlainToolCalls(content);

        Pattern jsonBlockPattern = Pattern.compile(JSON_CODE_BLOCK_REGEX);
        Matcher matcher = jsonBlockPattern.matcher(content);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String name = matcher.group(1);
            String arguments = matcher.group(2);
            String replacement = "<tool_call>{\"name\":\"" + name + "\",\"arguments\":" + arguments + "}</tool_call>";
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String normalizeToolNameBlocks(String content, String regex, String mode) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(content);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String name = matcher.group(1);
            String arguments = matcher.group(2);
            String realName = normalizeToolName(name);
            String replacement = "<tool_call>{\"name\":\"" + realName + "\",\"arguments\":" + arguments + "}</tool_call>";
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String normalizePlainToolCalls(String content) {
        Pattern pattern = Pattern.compile(PLAIN_TOOL_CALL_REGEX);
        Matcher matcher = pattern.matcher(content);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String name = matcher.group(1);
            String arguments = matcher.group(2);
            String realName = normalizeToolName(name);
            String replacement = "<tool_call>{\"name\":\"" + realName + "\",\"arguments\":" + arguments + "}</tool_call>";
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String normalizeFunctionCalls(String content) {
        Pattern functionCallPattern = Pattern.compile(FUNCTION_CALL_REGEX);
        Matcher matcher = functionCallPattern.matcher(content);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String name = matcher.group(1);
            String arguments = matcher.group(2);
            String realName = normalizeToolName(name);
            String paramName = getParamName(realName);
            String argsJson = escapeJsonString(paramName, arguments);
            String replacement = "<tool_call>{\"name\":\"" + realName + "\",\"arguments\":" + argsJson + "}</tool_call>";
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String normalizeToolName(String name) {
        String lower = name.toLowerCase();
        if (lower.contains("python") || lower.equals("py")) {
            return "code_python";
        }
        if ((lower.contains("java") && !lower.contains("script")) || lower.equals("javac")) {
            return "code_java";
        }
        if (lower.contains("sql") || lower.contains("query") || lower.contains("database") || lower.equals("db")) {
            return "db_query";
        }
        if (lower.contains("http") || lower.contains("web") || lower.contains("url") || lower.contains("request")) {
            return "http_request";
        }
        return name;
    }

    private String getParamName(String toolName) {
        return switch (toolName) {
            case "db_query" -> "sql";
            case "http_request" -> "url";
            case "code_java", "code_python" -> "code";
            default -> "value";
        };
    }

    private String escapeJsonString(String key, String value) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"").append(key).append("\":\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        sb.append("\"");
        return sb.toString();
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
            prompt.append("以下是与用户问题相关的参考信息:\n\n").append(ragContext).append("\n\n");
            prompt.append("回答要求：\n");
        prompt.append("1. 如果用户问题与参考信息相关，请优先基于参考信息回答。\n");
        prompt.append("2. 如果参考信息足以回答，请直接给出答案。\n");
        prompt.append("3. 如果参考信息不足，请基于已有信息尽力回答或说明需要补充资料，不要调用数据库查询、HTTP 请求等工具去获取知识库内容。\n");
        prompt.append("4. 不要回答\"没有收到文件\"或\"没有看到知识库\"，因为参考信息已经提供。\n");
        prompt.append("5. 当需要调用工具时，直接输出工具调用格式，不要解释。\n");
        prompt.append("6. 本对话中已提供知识库上下文，无需调用工具查询 localhost:8082 或数据库表。");
        }
        return prompt.toString();
    }
}
