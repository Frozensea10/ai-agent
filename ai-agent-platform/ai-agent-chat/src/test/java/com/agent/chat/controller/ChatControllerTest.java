package com.agent.chat.controller;

import com.agent.chat.dto.CreateSessionRequest;
import com.agent.chat.dto.SSEMessage;
import com.agent.chat.dto.SendMessageRequest;
import com.agent.chat.entity.ChatMessage;
import com.agent.chat.entity.ChatSession;
import com.agent.chat.feign.AgentFeignClient;
import com.agent.chat.feign.KnowledgeFeignClient;
import com.agent.chat.llm.ChatLLMService;
import com.agent.chat.memory.ChatMemoryProvider;
import com.agent.chat.service.MessageService;
import com.agent.chat.service.SessionService;
import com.agent.common.dto.AgentConfigDTO;
import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.common.exception.GlobalExceptionHandler;
import com.agent.common.handler.MyMetaObjectHandler;
import com.agent.common.result.Result;
import com.agent.core.llm.service.LLMService;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import reactor.core.publisher.Flux;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(GlobalExceptionHandler.class)
@WebMvcTest(controllers = ChatController.class,
        excludeAutoConfiguration = {
                org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
                org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration.class,
                org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration.class,
                org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration.class,
                org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class,
                org.springframework.cloud.client.serviceregistry.ServiceRegistryAutoConfiguration.class,
                com.alibaba.cloud.nacos.discovery.NacosDiscoveryAutoConfiguration.class,
                org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration.class,
                org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,
                org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration.class
        })
class ChatControllerTest {

    @TestConfiguration
    static class TestDataConfig {

        @Bean
        public DataSource dataSource() {
            return org.mockito.Mockito.mock(DataSource.class);
        }

        @Bean
        public MyMetaObjectHandler metaObjectHandler() {
            return org.mockito.Mockito.mock(MyMetaObjectHandler.class);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SessionService sessionService;

    @MockBean
    private MessageService messageService;

    @MockBean
    private ChatLLMService chatLLMService;

    @MockBean
    private LLMService llmService;

    @MockBean
    private AgentFeignClient agentFeignClient;

    @MockBean
    private KnowledgeFeignClient knowledgeFeignClient;

    @MockBean
    private ChatMemoryProvider chatMemoryProvider;

    private static final Long USER_ID = 1L;
    private static final String SESSION_ID = "session1234567890";
    private static final Long AGENT_ID = 2L;
    private static final Long KB_ID = 3L;

    private ChatSession createSession() {
        ChatSession session = new ChatSession();
        session.setId(1L);
        session.setSessionId(SESSION_ID);
        session.setUserId(USER_ID);
        session.setAgentId(AGENT_ID);
        session.setKbId(KB_ID);
        session.setSessionTitle("测试会话");
        session.setMessageCount(0);
        session.setStatus(1);
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        return session;
    }

    private ChatMessage createMessage(String role, String content) {
        ChatMessage message = new ChatMessage();
        message.setId(1L);
        message.setMessageId("msg_abc123");
        message.setSessionId(SESSION_ID);
        message.setRole(role);
        message.setContent(content);
        message.setContentType("text");
        message.setCreatedAt(LocalDateTime.now());
        return message;
    }

    private AgentConfigDTO createAgentConfig() {
        AgentConfigDTO agent = new AgentConfigDTO();
        agent.setId(AGENT_ID);
        agent.setAgentName("测试助手");
        agent.setModelProvider("openai");
        agent.setModelName("gpt-4o-mini");
        agent.setSystemPrompt("你是一个 helpful assistant。");
        agent.setTemperature(0.7);
        agent.setMaxTokens(1024);
        agent.setMemoryType("window");
        agent.setMemoryMaxMessages(10);
        return agent;
    }

    @Test
    @DisplayName("listSessions: 返回当前用户会话列表")
    void listSessions_shouldReturnSessionList() throws Exception {
        when(sessionService.listSessions(USER_ID)).thenReturn(List.of(createSession()));

        mockMvc.perform(get("/api/v1/sessions")
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data.size()", is(1)))
                .andExpect(jsonPath("$.data[0].sessionId", is(SESSION_ID)));
    }

    @Test
    @DisplayName("createSession: 创建会话成功")
    void createSession_shouldReturnSuccess() throws Exception {
        when(sessionService.createSession(USER_ID, AGENT_ID, KB_ID, "测试会话"))
                .thenReturn(createSession());

        CreateSessionRequest request = new CreateSessionRequest();
        request.setAgentId(AGENT_ID);
        request.setKbId(KB_ID);
        request.setTitle("测试会话");

        mockMvc.perform(post("/api/v1/sessions")
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data.sessionId", is(SESSION_ID)))
                .andExpect(jsonPath("$.data.sessionTitle", is("测试会话")));
    }

    @Test
    @DisplayName("createSession: Agent ID 为空返回 400")
    void createSession_shouldReturnBadRequestWhenInvalid() throws Exception {
        CreateSessionRequest request = new CreateSessionRequest();
        request.setKbId(KB_ID);
        request.setTitle("测试会话");

        mockMvc.perform(post("/api/v1/sessions")
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(400)));
    }

    @Test
    @DisplayName("deleteSession: 删除会话成功")
    void deleteSession_shouldReturnSuccess() throws Exception {
        doNothing().when(sessionService).deleteSession(SESSION_ID, USER_ID);
        doNothing().when(chatMemoryProvider).clear(SESSION_ID);

        mockMvc.perform(delete("/api/v1/sessions/{sessionId}", SESSION_ID)
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)));
    }

    @Test
    @DisplayName("getMessages: 查询消息历史成功")
    void getMessages_shouldReturnMessageList() throws Exception {
        when(sessionService.getSession(SESSION_ID, USER_ID)).thenReturn(createSession());
        when(messageService.getMessageHistory(SESSION_ID)).thenReturn(List.of(
                createMessage("user", "你好"),
                createMessage("assistant", "你好！有什么可以帮您的？")
        ));

        mockMvc.perform(get("/api/v1/sessions/{sessionId}/messages", SESSION_ID)
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data.size()", is(2)))
                .andExpect(jsonPath("$.data[0].role", is("user")))
                .andExpect(jsonPath("$.data[1].role", is("assistant")));
    }

    @Test
    @DisplayName("sendMessage: 发送消息并返回 AI 回复")
    void sendMessage_shouldReturnAiMessage() throws Exception {
        AgentConfigDTO agent = createAgentConfig();
        when(sessionService.getSession(SESSION_ID, USER_ID)).thenReturn(createSession());
        when(agentFeignClient.getAgent(AGENT_ID, USER_ID)).thenReturn(Result.success(agent));
        when(knowledgeFeignClient.ragQuery(anyString(), any(KnowledgeFeignClient.RAGQueryRequest.class), eq(USER_ID)))
                .thenReturn(Result.success(null));

        ChatModel chatModel = org.mockito.Mockito.mock(ChatModel.class);
        when(llmService.createChatModel(anyString(), anyString(), anyDouble(), anyInt())).thenReturn(chatModel);

        when(chatLLMService.chat(anyString(), anyString(), anyString(), any(), anyString(), anyInt(), any(ChatModel.class)))
                .thenReturn("AI 回复内容");

        when(messageService.saveUserMessage(SESSION_ID, "你好")).thenReturn(createMessage("user", "你好"));

        ChatMessage aiMessage = createMessage("assistant", "AI 回复内容");
        aiMessage.setModelName(agent.getModelName());
        when(messageService.saveAssistantMessage(SESSION_ID, "AI 回复内容", agent.getModelName(), null))
                .thenReturn(aiMessage);
        doNothing().when(sessionService).updateMessageCount(SESSION_ID);

        SendMessageRequest request = new SendMessageRequest();
        request.setContent("你好");
        request.setAgentId(AGENT_ID);
        request.setKbId(KB_ID);
        request.setKbCode("test-kb");
        request.setSessionId(SESSION_ID);

        mockMvc.perform(post("/api/v1/sessions/{sessionId}/messages", SESSION_ID)
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data.role", is("assistant")))
                .andExpect(jsonPath("$.data.content", is("AI 回复内容")))
                .andExpect(jsonPath("$.data.modelName", is("gpt-4o-mini")));
    }

    @Test
    @DisplayName("streamChat: 流式对话返回 SSE 数据")
    void streamChat_shouldReturnSseStream() throws Exception {
        AgentConfigDTO agent = createAgentConfig();
        when(sessionService.getSession(SESSION_ID, USER_ID)).thenReturn(createSession());
        when(agentFeignClient.getAgent(AGENT_ID, USER_ID)).thenReturn(Result.success(agent));

        StreamingChatModel streamingModel = org.mockito.Mockito.mock(StreamingChatModel.class);
        when(llmService.createStreamingModel(anyString(), anyString(), anyDouble(), anyInt())).thenReturn(streamingModel);

        when(messageService.saveUserMessage(SESSION_ID, "你好")).thenReturn(createMessage("user", "你好"));
        when(chatLLMService.streamChat(anyString(), anyString(), anyString(), any(), anyString(), anyInt(),
                any(StreamingChatModel.class), anyString()))
                .thenReturn(Flux.just(SSEMessage.start("msg-1"), SSEMessage.content("你好")));

        MvcResult mvcResult = mockMvc.perform(get("/api/v1/sessions/{sessionId}/stream", SESSION_ID)
                        .header("X-User-Id", USER_ID)
                        .param("content", "你好")
                        .param("agentId", AGENT_ID.toString()))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    assertTrue(body.contains("\"type\":\"start\""));
                    assertTrue(body.contains("\"type\":\"content\""));
                    assertTrue(body.contains("你好"));
                });
    }

    @Test
    @DisplayName("streamChat: 会话不存在时返回错误 SSE")
    void streamChat_shouldReturnErrorWhenSessionNotFound() throws Exception {
        when(sessionService.getSession(SESSION_ID, USER_ID))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND.getCode(), "会话不存在"));

        MvcResult mvcResult = mockMvc.perform(get("/api/v1/sessions/{sessionId}/stream", SESSION_ID)
                        .header("X-User-Id", USER_ID)
                        .param("content", "你好")
                        .param("agentId", AGENT_ID.toString()))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    assertTrue(body.contains("\"type\":\"error\""));
                    assertTrue(body.contains("会话不存在"));
                });
    }
}
