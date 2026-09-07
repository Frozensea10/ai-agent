package com.agent.chat.controller;

import com.agent.chat.dto.CreateSessionRequest;
import com.agent.chat.dto.SSEMessage;
import com.agent.chat.dto.SendMessageRequest;
import com.agent.chat.service.ChatBizService;
import com.agent.chat.vo.ChatMessageVO;
import com.agent.chat.vo.ChatSessionVO;
import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.common.exception.GlobalExceptionHandler;
import com.agent.common.handler.MyMetaObjectHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private ChatBizService chatBizService;

    private static final Long USER_ID = 1L;
    private static final String SESSION_ID = "session1234567890";
    private static final Long AGENT_ID = 2L;
    private static final Long KB_ID = 3L;

    private ChatSessionVO createSessionVO() {
        ChatSessionVO vo = new ChatSessionVO();
        vo.setId(1L);
        vo.setSessionId(SESSION_ID);
        vo.setAgentId(AGENT_ID);
        vo.setKbId(KB_ID);
        vo.setKbCode("test-kb");
        vo.setSessionTitle("测试会话");
        vo.setMessageCount(0);
        vo.setStatus(1);
        vo.setCreatedAt(LocalDateTime.now());
        vo.setUpdatedAt(LocalDateTime.now());
        return vo;
    }

    private ChatMessageVO createMessageVO(String role, String content) {
        ChatMessageVO vo = new ChatMessageVO();
        vo.setId(1L);
        vo.setMessageId("msg_abc123");
        vo.setRole(role);
        vo.setContent(content);
        vo.setContentType("text");
        vo.setCreatedAt(LocalDateTime.now());
        return vo;
    }

    @Test
    @DisplayName("listSessions: 返回当前用户会话列表")
    void listSessions_shouldReturnSessionList() throws Exception {
        when(chatBizService.listSessions(USER_ID)).thenReturn(List.of(createSessionVO()));

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
        when(chatBizService.createSession(eq(USER_ID), any(CreateSessionRequest.class)))
                .thenReturn(createSessionVO());

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
        doNothing().when(chatBizService).deleteSession(SESSION_ID, USER_ID);

        mockMvc.perform(delete("/api/v1/sessions/{sessionId}", SESSION_ID)
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)));
    }

    @Test
    @DisplayName("getMessages: 查询消息历史成功")
    void getMessages_shouldReturnMessageList() throws Exception {
        when(chatBizService.getMessages(SESSION_ID, USER_ID)).thenReturn(List.of(
                createMessageVO("user", "你好"),
                createMessageVO("assistant", "你好！有什么可以帮您的？")
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
        ChatMessageVO aiMessage = createMessageVO("assistant", "AI 回复内容");
        aiMessage.setModelName("gpt-4o-mini");
        when(chatBizService.sendMessage(eq(SESSION_ID), any(SendMessageRequest.class), eq(USER_ID)))
                .thenReturn(aiMessage);

        SendMessageRequest request = new SendMessageRequest();
        request.setContent("你好");
        request.setAgentId(AGENT_ID);

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
        when(chatBizService.streamChat(eq(SESSION_ID), eq("你好"), eq(AGENT_ID), isNull(), isNull(), isNull(), eq(USER_ID)))
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
        when(chatBizService.streamChat(eq(SESSION_ID), eq("你好"), eq(AGENT_ID), isNull(), isNull(), isNull(), eq(USER_ID)))
                .thenReturn(Flux.just(SSEMessage.error("会话不存在")));

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
