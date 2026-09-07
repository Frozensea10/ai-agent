package com.agent.knowledge.service;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.knowledge.chunk.DocumentChunker;
import com.agent.knowledge.embedding.EmbeddingService;
import com.agent.knowledge.service.DocumentAsyncProcessor;
import com.agent.knowledge.entity.KnowledgeBase;
import com.agent.knowledge.entity.KnowledgeChunk;
import com.agent.knowledge.entity.KnowledgeDocument;
import com.agent.knowledge.mapper.KnowledgeBaseMapper;
import com.agent.knowledge.mapper.KnowledgeChunkMapper;
import com.agent.knowledge.mapper.KnowledgeDocumentMapper;
import com.agent.knowledge.parser.DocumentParser;
import com.agent.knowledge.vector.QdrantService;
import com.agent.knowledge.vo.KnowledgeBaseVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseServiceTest {

    @Mock
    private KnowledgeBaseMapper kbMapper;

    @Mock
    private KnowledgeDocumentMapper docMapper;

    @Mock
    private KnowledgeChunkMapper chunkMapper;

    @Mock
    private DocumentParser documentParser;

    @Mock
    private DocumentChunker documentChunker;

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private QdrantService qdrantService;

    @Mock
    private DocumentAsyncProcessor documentAsyncProcessor;

    @InjectMocks
    private KnowledgeBaseServiceImpl knowledgeBaseService;

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long KB_ID = 1L;
    private static final String KB_CODE = "test-kb";
    private static final String KB_NAME = "Test Knowledge Base";

    private KnowledgeBase createKnowledgeBase(Long id, String kbCode, String kbName, Long createdBy) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(id);
        kb.setKbCode(kbCode);
        kb.setKbName(kbName);
        kb.setDescription("Description");
        kb.setEmbeddingModel(KnowledgeBaseConstants.DEFAULT_EMBEDDING_MODEL);
        kb.setDocumentCount(0);
        kb.setStatus(KnowledgeBaseConstants.ACTIVE_STATUS);
        kb.setCreatedBy(createdBy);
        return kb;
    }

    @BeforeEach
    void setUp() {
        lenient().when(embeddingService.getVectorSize(any(), any())).thenReturn(1536);
    }

    @Test
    @DisplayName("createKnowledgeBase: 创建成功")
    void createKnowledgeBase_shouldSucceed() throws ExecutionException, InterruptedException {
        when(kbMapper.selectByCode(KB_CODE)).thenReturn(null);
        when(kbMapper.insert(any(KnowledgeBase.class))).thenReturn(1);
        doNothing().when(qdrantService).createCollection(anyString(), eq(1536));

        KnowledgeBaseVO result = knowledgeBaseService.createKnowledgeBase(
                KB_NAME, KB_CODE, "Description", "text-embedding-3-small", "openai", USER_ID);

        assertNotNull(result);
        assertEquals(KB_NAME, result.getKbName());
        assertEquals(KB_CODE, result.getKbCode());
        assertEquals(KnowledgeBaseConstants.ACTIVE_STATUS, result.getStatus());
        verify(kbMapper).insert(any(KnowledgeBase.class));
        verify(qdrantService).createCollection(KB_CODE, 1536);
    }

    @Test
    @DisplayName("createKnowledgeBase: 编码已存在时抛出参数错误")
    void createKnowledgeBase_shouldThrowWhenCodeExists() throws ExecutionException, InterruptedException {
        when(kbMapper.selectByCode(KB_CODE)).thenReturn(new KnowledgeBase());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> knowledgeBaseService.createKnowledgeBase(KB_NAME, KB_CODE, "Description", null, null, USER_ID));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        verify(kbMapper, never()).insert(any(KnowledgeBase.class));
        verify(qdrantService, never()).createCollection(anyString(), any(Integer.class));
    }

    @Test
    @DisplayName("createKnowledgeBase: 向量服务异常时回滚")
    void createKnowledgeBase_shouldThrowWhenQdrantFails() throws ExecutionException, InterruptedException {
        when(kbMapper.selectByCode(KB_CODE)).thenReturn(null);
        when(kbMapper.insert(any(KnowledgeBase.class))).thenReturn(1);
        doThrow(new RuntimeException("Qdrant error")).when(qdrantService).createCollection(anyString(), any(Integer.class));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> knowledgeBaseService.createKnowledgeBase(KB_NAME, KB_CODE, "Description", null, null, USER_ID));

        assertEquals(ErrorCode.VECTOR_SERVICE_ERROR.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains("向量数据库初始化失败"));
    }

    @Test
    @DisplayName("listKnowledgeBases: 返回当前用户知识库列表")
    void listKnowledgeBases_shouldReturnUserList() {
        KnowledgeBase kb1 = createKnowledgeBase(1L, "kb-1", "KB1", USER_ID);
        KnowledgeBase kb2 = createKnowledgeBase(2L, "kb-2", "KB2", USER_ID);
        when(kbMapper.selectByUserId(USER_ID)).thenReturn(List.of(kb1, kb2));

        List<KnowledgeBaseVO> result = knowledgeBaseService.listKnowledgeBases(USER_ID);

        assertEquals(2, result.size());
        assertEquals("KB1", result.get(0).getKbName());
        assertEquals("KB2", result.get(1).getKbName());
    }

    @Test
    @DisplayName("getKnowledgeBaseById: 查询成功")
    void getKnowledgeBaseById_shouldSucceed() {
        KnowledgeBase kb = createKnowledgeBase(KB_ID, KB_CODE, KB_NAME, USER_ID);
        when(kbMapper.selectById(KB_ID)).thenReturn(kb);

        KnowledgeBaseVO result = knowledgeBaseService.getKnowledgeBase(KB_ID, USER_ID);

        assertEquals(KB_ID, result.getId());
        assertEquals(KB_NAME, result.getKbName());
    }

    @Test
    @DisplayName("getKnowledgeBaseById: 不存在时抛出 NOT_FOUND")
    void getKnowledgeBaseById_shouldThrowWhenNotFound() {
        when(kbMapper.selectById(KB_ID)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> knowledgeBaseService.getKnowledgeBase(KB_ID, USER_ID));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("getKnowledgeBaseById: 非创建者访问时抛出 FORBIDDEN")
    void getKnowledgeBaseById_shouldThrowWhenForbidden() {
        KnowledgeBase kb = createKnowledgeBase(KB_ID, KB_CODE, KB_NAME, OTHER_USER_ID);
        when(kbMapper.selectById(KB_ID)).thenReturn(kb);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> knowledgeBaseService.getKnowledgeBase(KB_ID, USER_ID));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("updateKnowledgeBase: 更新成功")
    void updateKnowledgeBase_shouldSucceed() {
        KnowledgeBase kb = createKnowledgeBase(KB_ID, KB_CODE, KB_NAME, USER_ID);
        when(kbMapper.selectById(KB_ID)).thenReturn(kb);
        when(kbMapper.updateById(kb)).thenReturn(1);

        KnowledgeBaseVO result = knowledgeBaseService.updateKnowledgeBase(
                KB_ID, "Updated Name", "Updated Description", "text-embedding-3-large", "openai", USER_ID);

        assertEquals("Updated Name", result.getKbName());
        assertEquals("Updated Description", result.getDescription());
        assertEquals("text-embedding-3-large", result.getEmbeddingModel());
        verify(kbMapper).updateById(kb);
    }

    @Test
    @DisplayName("updateKnowledgeBase: 部分字段更新成功")
    void updateKnowledgeBase_shouldUpdatePartially() {
        KnowledgeBase kb = createKnowledgeBase(KB_ID, KB_CODE, KB_NAME, USER_ID);
        when(kbMapper.selectById(KB_ID)).thenReturn(kb);
        when(kbMapper.updateById(kb)).thenReturn(1);

        KnowledgeBaseVO result = knowledgeBaseService.updateKnowledgeBase(
                KB_ID, "Updated Name", null, null, null, USER_ID);

        assertEquals("Updated Name", result.getKbName());
        assertEquals("Description", result.getDescription());
        assertEquals(KnowledgeBaseConstants.DEFAULT_EMBEDDING_MODEL, result.getEmbeddingModel());
    }

    @Test
    @DisplayName("updateKnowledgeBase: 不存在时抛出 NOT_FOUND")
    void updateKnowledgeBase_shouldThrowWhenNotFound() {
        when(kbMapper.selectById(KB_ID)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> knowledgeBaseService.updateKnowledgeBase(KB_ID, KB_NAME, null, null, null, USER_ID));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("updateKnowledgeBase: 非创建者更新时抛出 FORBIDDEN")
    void updateKnowledgeBase_shouldThrowWhenForbidden() {
        KnowledgeBase kb = createKnowledgeBase(KB_ID, KB_CODE, KB_NAME, OTHER_USER_ID);
        when(kbMapper.selectById(KB_ID)).thenReturn(kb);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> knowledgeBaseService.updateKnowledgeBase(KB_ID, KB_NAME, null, null, null, USER_ID));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), exception.getCode());
        verify(kbMapper, never()).updateById(any(KnowledgeBase.class));
    }

    @Test
    @DisplayName("deleteKnowledgeBase: 删除成功")
    void deleteKnowledgeBase_shouldSucceed() throws ExecutionException, InterruptedException {
        KnowledgeBase kb = createKnowledgeBase(KB_ID, KB_CODE, KB_NAME, USER_ID);
        when(kbMapper.selectById(KB_ID)).thenReturn(kb);
        when(docMapper.selectByKbId(KB_ID)).thenReturn(Collections.emptyList());
        doNothing().when(qdrantService).deleteCollection(KB_CODE);
        when(kbMapper.deleteById(KB_ID)).thenReturn(1);

        knowledgeBaseService.deleteKnowledgeBase(KB_ID, USER_ID);

        verify(kbMapper).deleteById(KB_ID);
        verify(qdrantService).deleteCollection(KB_CODE);
    }

    @Test
    @DisplayName("deleteKnowledgeBase: 删除包含文档时同步清理向量数据")
    void deleteKnowledgeBase_shouldCleanVectorPoints() throws ExecutionException, InterruptedException {
        KnowledgeBase kb = createKnowledgeBase(KB_ID, KB_CODE, KB_NAME, USER_ID);
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setId(10L);
        doc.setKbId(KB_ID);
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setId(100L);
        chunk.setDocId(10L);
        chunk.setVectorId("vec-001");

        when(kbMapper.selectById(KB_ID)).thenReturn(kb);
        when(docMapper.selectByKbId(KB_ID)).thenReturn(List.of(doc));
        when(chunkMapper.selectByDocId(10L)).thenReturn(List.of(chunk));
        doNothing().when(qdrantService).deletePoints(eq(KB_CODE), any(List.class));
        doNothing().when(qdrantService).deleteCollection(KB_CODE);
        when(kbMapper.deleteById(KB_ID)).thenReturn(1);

        knowledgeBaseService.deleteKnowledgeBase(KB_ID, USER_ID);

        verify(qdrantService).deletePoints(eq(KB_CODE), any(List.class));
        verify(qdrantService).deleteCollection(KB_CODE);
        verify(kbMapper).deleteById(KB_ID);
    }

    @Test
    @DisplayName("deleteKnowledgeBase: 不存在时抛出 NOT_FOUND")
    void deleteKnowledgeBase_shouldThrowWhenNotFound() {
        when(kbMapper.selectById(KB_ID)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> knowledgeBaseService.deleteKnowledgeBase(KB_ID, USER_ID));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("deleteKnowledgeBase: 非创建者删除时抛出 FORBIDDEN")
    void deleteKnowledgeBase_shouldThrowWhenForbidden() {
        KnowledgeBase kb = createKnowledgeBase(KB_ID, KB_CODE, KB_NAME, OTHER_USER_ID);
        when(kbMapper.selectById(KB_ID)).thenReturn(kb);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> knowledgeBaseService.deleteKnowledgeBase(KB_ID, USER_ID));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), exception.getCode());
        verify(kbMapper, never()).deleteById(any(Long.class));
    }
}
