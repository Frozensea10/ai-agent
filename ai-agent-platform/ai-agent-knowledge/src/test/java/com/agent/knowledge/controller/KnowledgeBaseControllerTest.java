package com.agent.knowledge.controller;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.common.exception.GlobalExceptionHandler;
import com.agent.common.handler.MyMetaObjectHandler;
import com.agent.knowledge.rag.RAGService;
import com.agent.knowledge.service.KnowledgeBaseService;
import com.agent.knowledge.vo.KnowledgeBaseVO;
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

import javax.sql.DataSource;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(GlobalExceptionHandler.class)
@WebMvcTest(controllers = KnowledgeBaseController.class,
        excludeAutoConfiguration = {
                org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
                org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration.class,
                org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration.class,
                org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration.class,
                org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class
        })
class KnowledgeBaseControllerTest {

    @TestConfiguration
    static class TestDataConfig {

        @Bean
        public DataSource dataSource() {
            return mock(DataSource.class);
        }

        @Bean
        public MyMetaObjectHandler metaObjectHandler() {
            return mock(MyMetaObjectHandler.class);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private KnowledgeBaseService knowledgeBaseService;

    @MockBean
    private RAGService ragService;

    private static final Long USER_ID = 1L;
    private static final Long KB_ID = 1L;

    private KnowledgeBaseVO createKnowledgeBaseVO(Long id, String kbCode, String kbName) {
        KnowledgeBaseVO vo = new KnowledgeBaseVO();
        vo.setId(id);
        vo.setKbCode(kbCode);
        vo.setKbName(kbName);
        vo.setDescription("Description");
        vo.setEmbeddingModel("text-embedding-3-small");
        vo.setDocumentCount(0);
        vo.setStatus(1);
        return vo;
    }

    @Test
    @DisplayName("create: 创建知识库成功")
    void create_shouldReturnSuccess() throws Exception {
        KnowledgeBaseVO vo = createKnowledgeBaseVO(KB_ID, "test-kb", "Test KB");
        when(knowledgeBaseService.createKnowledgeBase(anyString(), anyString(), anyString(), anyString(), eq(USER_ID)))
                .thenReturn(vo);

        KnowledgeBaseController.CreateKBRequest request = new KnowledgeBaseController.CreateKBRequest();
        request.setKbName("Test KB");
        request.setKbCode("test-kb");
        request.setDescription("Description");
        request.setEmbeddingModel("text-embedding-3-small");

        mockMvc.perform(post("/api/v1/knowledge-bases")
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data.kbCode", is("test-kb")))
                .andExpect(jsonPath("$.data.kbName", is("Test KB")));
    }

    @Test
    @DisplayName("create: 参数校验失败返回 400")
    void create_shouldReturnBadRequestWhenInvalid() throws Exception {
        KnowledgeBaseController.CreateKBRequest request = new KnowledgeBaseController.CreateKBRequest();
        request.setKbName("");
        request.setKbCode("");

        mockMvc.perform(post("/api/v1/knowledge-bases")
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(400)));
    }

    @Test
    @DisplayName("list: 返回当前用户知识库列表")
    void list_shouldReturnKnowledgeBaseList() throws Exception {
        when(knowledgeBaseService.listKnowledgeBases(USER_ID))
                .thenReturn(List.of(
                        createKnowledgeBaseVO(1L, "kb-1", "KB1"),
                        createKnowledgeBaseVO(2L, "kb-2", "KB2")));

        mockMvc.perform(get("/api/v1/knowledge-bases")
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data.size()", is(2)))
                .andExpect(jsonPath("$.data[0].kbCode", is("kb-1")));
    }

    @Test
    @DisplayName("get: 查询知识库成功")
    void get_shouldReturnKnowledgeBase() throws Exception {
        when(knowledgeBaseService.getKnowledgeBase(KB_ID, USER_ID))
                .thenReturn(createKnowledgeBaseVO(KB_ID, "test-kb", "Test KB"));

        mockMvc.perform(get("/api/v1/knowledge-bases/{id}", KB_ID)
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data.id", is(1)))
                .andExpect(jsonPath("$.data.kbName", is("Test KB")));
    }

    @Test
    @DisplayName("get: 不存在时返回 404")
    void get_shouldReturnNotFound() throws Exception {
        when(knowledgeBaseService.getKnowledgeBase(KB_ID, USER_ID))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND.getCode(), "知识库不存在"));

        mockMvc.perform(get("/api/v1/knowledge-bases/{id}", KB_ID)
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(404)))
                .andExpect(jsonPath("$.message", is("知识库不存在")));
    }

    @Test
    @DisplayName("update: 更新知识库成功")
    void update_shouldReturnSuccess() throws Exception {
        KnowledgeBaseVO vo = createKnowledgeBaseVO(KB_ID, "test-kb", "Updated KB");
        when(knowledgeBaseService.updateKnowledgeBase(eq(KB_ID), anyString(), anyString(), anyString(), eq(USER_ID)))
                .thenReturn(vo);

        KnowledgeBaseController.UpdateKBRequest request = new KnowledgeBaseController.UpdateKBRequest();
        request.setKbName("Updated KB");
        request.setDescription("Updated Description");
        request.setEmbeddingModel("text-embedding-3-large");

        mockMvc.perform(put("/api/v1/knowledge-bases/{id}", KB_ID)
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data.kbName", is("Updated KB")));
    }

    @Test
    @DisplayName("update: 非创建者更新返回 403")
    void update_shouldReturnForbidden() throws Exception {
        when(knowledgeBaseService.updateKnowledgeBase(eq(KB_ID), anyString(), anyString(), anyString(), eq(USER_ID)))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN.getCode(), "无权更新该知识库"));

        KnowledgeBaseController.UpdateKBRequest request = new KnowledgeBaseController.UpdateKBRequest();
        request.setKbName("Updated KB");
        request.setDescription("Updated Description");
        request.setEmbeddingModel("text-embedding-3-large");

        mockMvc.perform(put("/api/v1/knowledge-bases/{id}", KB_ID)
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(403)))
                .andExpect(jsonPath("$.message", is("无权更新该知识库")));
    }

    @Test
    @DisplayName("delete: 删除知识库成功")
    void delete_shouldReturnSuccess() throws Exception {
        doNothing().when(knowledgeBaseService).deleteKnowledgeBase(KB_ID, USER_ID);

        mockMvc.perform(delete("/api/v1/knowledge-bases/{id}", KB_ID)
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)));
    }

    @Test
    @DisplayName("delete: 非创建者删除返回 403")
    void delete_shouldReturnForbidden() throws Exception {
        doThrow(new BusinessException(ErrorCode.FORBIDDEN.getCode(), "无权删除该知识库"))
                .when(knowledgeBaseService).deleteKnowledgeBase(KB_ID, USER_ID);

        mockMvc.perform(delete("/api/v1/knowledge-bases/{id}", KB_ID)
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(403)))
                .andExpect(jsonPath("$.message", is("无权删除该知识库")));
    }

    @Test
    @DisplayName("delete: 知识库不存在返回 404")
    void delete_shouldReturnNotFound() throws Exception {
        doThrow(new BusinessException(ErrorCode.NOT_FOUND.getCode(), "知识库不存在"))
                .when(knowledgeBaseService).deleteKnowledgeBase(KB_ID, USER_ID);

        mockMvc.perform(delete("/api/v1/knowledge-bases/{id}", KB_ID)
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(404)))
                .andExpect(jsonPath("$.message", is("知识库不存在")));
    }
}
