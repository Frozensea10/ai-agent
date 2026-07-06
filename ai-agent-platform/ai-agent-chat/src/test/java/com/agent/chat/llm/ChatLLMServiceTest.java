package com.agent.chat.llm;

import com.agent.chat.dto.SSEMessage;
import com.agent.chat.feign.McpFeignClient;
import com.agent.chat.memory.ChatMemoryProvider;
import com.agent.chat.service.MessageService;
import com.agent.common.result.Result;
import com.agent.mcp.dto.ToolExecuteResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatLLMServiceTest {

    @Mock
    private ChatMemoryProvider chatMemoryProvider;

    @Mock
    private MessageService messageService;

    @Mock
    private McpFeignClient mcpFeignClient;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ChatLLMService chatLLMService;

    @Mock
    private ChatModel chatModel;

    @Mock
    private StreamingChatModel streamingChatModel;

    private static final String SESSION_ID = "session123";
    private static final String USER_MESSAGE = "你好";
    private static final String SYSTEM_PROMPT = "你是一个 helpful assistant。";
    private static final String MEMORY_TYPE = "window";
    private static final Integer MAX_MESSAGES = 10;

    private List<ChatMessage> createHistory() {
        List<ChatMessage> history = new ArrayList<>();
        history.add(UserMessage.from(USER_MESSAGE));
        return history;
    }

    private ChatResponse createChatResponse(String aiText) {
        return ChatResponse.builder()
                .aiMessage(AiMessage.from(aiText))
                .build();
    }

    @Test
    @DisplayName("chat: 无 ragContext 时正常调用模型并返回结果")
    void chat_withoutRagContext_shouldReturnResponse() {
        when(chatMemoryProvider.getMessages(SESSION_ID, MEMORY_TYPE, MAX_MESSAGES)).thenReturn(createHistory());
        when(chatModel.chat(anyList())).thenReturn(createChatResponse("AI 回复"));
        doNothing().when(chatMemoryProvider).addUserMessage(SESSION_ID, MEMORY_TYPE, MAX_MESSAGES, USER_MESSAGE);
        doNothing().when(chatMemoryProvider).addAiMessage(SESSION_ID, MEMORY_TYPE, MAX_MESSAGES, "AI 回复");

        String result = chatLLMService.chat(SESSION_ID, USER_MESSAGE, SYSTEM_PROMPT, null, MEMORY_TYPE, MAX_MESSAGES, chatModel);

        assertEquals("AI 回复", result);
        ArgumentCaptor<List<ChatMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(chatModel).chat(captor.capture());
        List<ChatMessage> messages = captor.getValue();
        assertEquals(2, messages.size());
        assertTrue(messages.get(0) instanceof SystemMessage);
        assertEquals(SYSTEM_PROMPT, ((SystemMessage) messages.get(0)).text());
    }

    @Test
    @DisplayName("chat: 有 ragContext 时系统提示包含参考信息")
    void chat_withRagContext_shouldAppendRagContext() {
        String ragContext = "参考信息：AI Agent 是智能代理。";
        when(chatMemoryProvider.getMessages(SESSION_ID, MEMORY_TYPE, MAX_MESSAGES)).thenReturn(createHistory());
        when(chatModel.chat(anyList())).thenReturn(createChatResponse("基于参考信息回复"));
        doNothing().when(chatMemoryProvider).addUserMessage(SESSION_ID, MEMORY_TYPE, MAX_MESSAGES, USER_MESSAGE);
        doNothing().when(chatMemoryProvider).addAiMessage(SESSION_ID, MEMORY_TYPE, MAX_MESSAGES, "基于参考信息回复");

        String result = chatLLMService.chat(SESSION_ID, USER_MESSAGE, SYSTEM_PROMPT, ragContext, MEMORY_TYPE, MAX_MESSAGES, chatModel);

        assertEquals("基于参考信息回复", result);
        ArgumentCaptor<List<ChatMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(chatModel).chat(captor.capture());
        SystemMessage systemMessage = (SystemMessage) captor.getValue().get(0);
        assertTrue(systemMessage.text().contains(SYSTEM_PROMPT));
        assertTrue(systemMessage.text().contains(ragContext));
        assertTrue(systemMessage.text().contains("请基于以上参考信息回答用户问题"));
    }

    @Test
    @DisplayName("chat: 无系统提示和 ragContext 时不添加系统消息")
    void chat_withoutSystemPromptAndRag_shouldNotAddSystemMessage() {
        when(chatMemoryProvider.getMessages(SESSION_ID, MEMORY_TYPE, MAX_MESSAGES)).thenReturn(createHistory());
        when(chatModel.chat(anyList())).thenReturn(createChatResponse("AI 回复"));
        doNothing().when(chatMemoryProvider).addUserMessage(SESSION_ID, MEMORY_TYPE, MAX_MESSAGES, USER_MESSAGE);
        doNothing().when(chatMemoryProvider).addAiMessage(SESSION_ID, MEMORY_TYPE, MAX_MESSAGES, "AI 回复");

        chatLLMService.chat(SESSION_ID, USER_MESSAGE, null, null, MEMORY_TYPE, MAX_MESSAGES, chatModel);

        ArgumentCaptor<List<ChatMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(chatModel).chat(captor.capture());
        List<ChatMessage> messages = captor.getValue();
        assertEquals(1, messages.size());
        assertTrue(messages.get(0) instanceof UserMessage);
    }

    @Test
    @DisplayName("chat: 模型返回包含工具调用时调用 MCP 并替换结果")
    void chat_withToolCall_shouldExecuteToolAndReplace() throws Exception {
        String toolCallContent = "调用工具 <tool_call>{\"name\":\"http_request\",\"arguments\":{\"url\":\"https://example.com\"}}</tool_call>";
        when(chatMemoryProvider.getMessages(SESSION_ID, MEMORY_TYPE, MAX_MESSAGES)).thenReturn(createHistory());
        when(chatModel.chat(anyList())).thenReturn(createChatResponse(toolCallContent));
        doNothing().when(chatMemoryProvider).addUserMessage(SESSION_ID, MEMORY_TYPE, MAX_MESSAGES, USER_MESSAGE);

        ToolExecuteResult toolResult = new ToolExecuteResult();
        toolResult.setSuccess(true);
        toolResult.setData("OK");
        when(mcpFeignClient.executeTool(eq("http_request"), anyMap()))
                .thenReturn(Result.success(toolResult));

        String result = chatLLMService.chat(SESSION_ID, USER_MESSAGE, SYSTEM_PROMPT, null, MEMORY_TYPE, MAX_MESSAGES, chatModel);

        assertTrue(result.contains("[工具执行结果]"));
        assertTrue(result.contains("OK"));
        verify(mcpFeignClient).executeTool(eq("http_request"), anyMap());
    }

    @Test
    @DisplayName("streamChat: 无 ragContext 时返回 start、content 和 end SSE 消息")
    void streamChat_withoutRagContext_shouldEmitEvents() {
        when(chatMemoryProvider.getMessages(SESSION_ID, MEMORY_TYPE, MAX_MESSAGES)).thenReturn(createHistory());
        doNothing().when(chatMemoryProvider).addUserMessage(SESSION_ID, MEMORY_TYPE, MAX_MESSAGES, USER_MESSAGE);
        doNothing().when(chatMemoryProvider).addAiMessage(eq(SESSION_ID), eq(MEMORY_TYPE), eq(MAX_MESSAGES), anyString());
        when(messageService.saveAssistantMessage(eq(SESSION_ID), anyString(), anyString(), any()))
                .thenReturn(null);

        ChatResponse completeResponse = createChatResponse("AI 流式回复");

        doAnswer(invocation -> {
            List<ChatMessage> messages = invocation.getArgument(0);
            StreamingChatResponseHandler handler = invocation.getArgument(1);
            handler.onPartialResponse("AI ");
            handler.onPartialResponse("流式回复");
            handler.onCompleteResponse(completeResponse);
            return null;
        }).when(streamingChatModel).chat(anyList(), any(StreamingChatResponseHandler.class));

        Flux<SSEMessage> flux = chatLLMService.streamChat(SESSION_ID, USER_MESSAGE, SYSTEM_PROMPT, null, MEMORY_TYPE, MAX_MESSAGES, streamingChatModel, "gpt-4o-mini");

        StepVerifier.create(flux)
                .expectNextMatches(sse -> "start".equals(sse.getType()))
                .expectNextMatches(sse -> "AI ".equals(sse.getDelta()))
                .expectNextMatches(sse -> "流式回复".equals(sse.getDelta()))
                .expectNextMatches(sse -> "end".equals(sse.getType()))
                .verifyComplete();

        verify(messageService).saveAssistantMessage(eq(SESSION_ID), eq("AI 流式回复"), eq("gpt-4o-mini"), any());
    }

    @Test
    @DisplayName("streamChat: 有 ragContext 时系统提示包含参考信息")
    void streamChat_withRagContext_shouldAppendRagContext() {
        String ragContext = "参考信息：流式对话也支持 RAG。";
        when(chatMemoryProvider.getMessages(SESSION_ID, MEMORY_TYPE, MAX_MESSAGES)).thenReturn(createHistory());
        doNothing().when(chatMemoryProvider).addUserMessage(SESSION_ID, MEMORY_TYPE, MAX_MESSAGES, USER_MESSAGE);
        doNothing().when(chatMemoryProvider).addAiMessage(eq(SESSION_ID), eq(MEMORY_TYPE), eq(MAX_MESSAGES), anyString());
        when(messageService.saveAssistantMessage(eq(SESSION_ID), anyString(), anyString(), any()))
                .thenReturn(null);

        doAnswer(invocation -> {
            List<ChatMessage> messages = invocation.getArgument(0);
            StreamingChatResponseHandler handler = invocation.getArgument(1);
            SystemMessage systemMessage = (SystemMessage) messages.get(0);
            assertTrue(systemMessage.text().contains(SYSTEM_PROMPT));
            assertTrue(systemMessage.text().contains(ragContext));
            handler.onCompleteResponse(createChatResponse("回复"));
            return null;
        }).when(streamingChatModel).chat(anyList(), any(StreamingChatResponseHandler.class));

        Flux<SSEMessage> flux = chatLLMService.streamChat(SESSION_ID, USER_MESSAGE, SYSTEM_PROMPT, ragContext, MEMORY_TYPE, MAX_MESSAGES, streamingChatModel, "gpt-4o-mini");

        StepVerifier.create(flux)
                .expectNextCount(1)
                .expectNextMatches(sse -> "end".equals(sse.getType()))
                .verifyComplete();
    }

    @Test
    @DisplayName("streamChat: 模型回调 onError 时返回错误 SSE")
    void streamChat_whenModelError_shouldEmitError() {
        when(chatMemoryProvider.getMessages(SESSION_ID, MEMORY_TYPE, MAX_MESSAGES)).thenReturn(createHistory());
        doNothing().when(chatMemoryProvider).addUserMessage(SESSION_ID, MEMORY_TYPE, MAX_MESSAGES, USER_MESSAGE);

        doAnswer(invocation -> {
            StreamingChatResponseHandler handler = invocation.getArgument(1);
            handler.onError(new RuntimeException("模型调用失败"));
            return null;
        }).when(streamingChatModel).chat(anyList(), any(StreamingChatResponseHandler.class));

        Flux<SSEMessage> flux = chatLLMService.streamChat(SESSION_ID, USER_MESSAGE, SYSTEM_PROMPT, null, MEMORY_TYPE, MAX_MESSAGES, streamingChatModel, "gpt-4o-mini");

        StepVerifier.create(flux)
                .expectNextMatches(sse -> "start".equals(sse.getType()))
                .expectNextMatches(sse -> "error".equals(sse.getType()) || "流式对话失败: 模型调用失败".equals(sse.getDelta()))
                .verifyComplete();

        verify(chatMemoryProvider, never()).addAiMessage(eq(SESSION_ID), eq(MEMORY_TYPE), eq(MAX_MESSAGES), anyString());
    }
}
