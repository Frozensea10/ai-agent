package com.agent.knowledge.service;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.knowledge.chunk.DocumentChunker;
import com.agent.knowledge.embedding.EmbeddingService;
import com.agent.knowledge.entity.KnowledgeBase;
import com.agent.knowledge.entity.KnowledgeChunk;
import com.agent.knowledge.entity.KnowledgeDocument;
import com.agent.knowledge.mapper.KnowledgeBaseMapper;
import com.agent.knowledge.mapper.KnowledgeChunkMapper;
import com.agent.knowledge.mapper.KnowledgeDocumentMapper;
import com.agent.knowledge.parser.DocumentParser;
import com.agent.knowledge.vector.QdrantService;
import com.agent.knowledge.vo.KnowledgeBaseVO;
import com.agent.knowledge.vo.KnowledgeDocumentVO;
import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.grpc.Points;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.annotations.Update;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.agent.knowledge.service.KnowledgeBaseConstants.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {

    private final KnowledgeBaseMapper kbMapper;
    private final KnowledgeDocumentMapper docMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final DocumentParser documentParser;
    private final DocumentChunker documentChunker;
    private final EmbeddingService embeddingService;
    private final QdrantService qdrantService;

    @Transactional
    public KnowledgeBaseVO createKnowledgeBase(String kbName, String kbCode, String description, String embeddingModel, Long userId) {
        KnowledgeBase existing = kbMapper.selectByCode(kbCode);
        if (existing != null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "知识库编码已存在");
        }

        KnowledgeBase kb = new KnowledgeBase();
        kb.setKbName(kbName);
        kb.setKbCode(kbCode);
        kb.setDescription(description);
        kb.setEmbeddingModel(embeddingModel != null ? embeddingModel : DEFAULT_EMBEDDING_MODEL);
        kb.setDocumentCount(0);
        kb.setStatus(ACTIVE_STATUS);
        kb.setCreatedBy(userId);
        kbMapper.insert(kb);

        try {
            qdrantService.createCollection(kbCode, embeddingService.getVectorSize());
        } catch (Exception e) {
            log.error("创建 Qdrant Collection 失败", e);
            throw new BusinessException(ErrorCode.VECTOR_SERVICE_ERROR.getCode(), "向量数据库初始化失败: " + e.getMessage());
        }

        return convertToBaseVO(kb);
    }

    @Transactional
    public KnowledgeBaseVO updateKnowledgeBase(Long id, String kbName, String description, String embeddingModel, Long userId) {
        KnowledgeBase kb = kbMapper.selectById(id);
        if (kb == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "知识库不存在");
        }
        if (!kb.getCreatedBy().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "无权更新该知识库");
        }
        if (kbName != null) {
            kb.setKbName(kbName);
        }
        if (description != null) {
            kb.setDescription(description);
        }
        if (embeddingModel != null) {
            kb.setEmbeddingModel(embeddingModel);
        }
        kbMapper.updateById(kb);
        return convertToBaseVO(kb);
    }

    public KnowledgeBaseVO getKnowledgeBaseByCode(String kbCode, Long userId) {
        KnowledgeBase kb = kbMapper.selectByCode(kbCode);
        if (kb == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "知识库不存在");
        }
        if (!kb.getCreatedBy().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "无权访问该知识库");
        }
        return convertToBaseVO(kb);
    }

    public List<KnowledgeBaseVO> listKnowledgeBases(Long userId) {
        return kbMapper.selectByUserId(userId).stream()
                .map(this::convertToBaseVO)
                .collect(Collectors.toList());
    }

    public List<KnowledgeBaseVO> listAllKnowledgeBases() {
        return kbMapper.selectAllActive().stream()
                .map(this::convertToBaseVO)
                .collect(Collectors.toList());
    }

    public KnowledgeBaseVO getKnowledgeBase(Long id, Long userId) {
        KnowledgeBase kb = kbMapper.selectById(id);
        if (kb == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "知识库不存在");
        }
        if (!kb.getCreatedBy().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "无权访问该知识库");
        }
        return convertToBaseVO(kb);
    }

    @Transactional
    public void deleteKnowledgeBase(Long id, Long userId) {
        KnowledgeBase kb = kbMapper.selectById(id);
        if (kb == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "知识库不存在");
        }
        if (!kb.getCreatedBy().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "无权删除该知识库");
        }

        List<KnowledgeDocument> docs = docMapper.selectByKbId(id);
        for (KnowledgeDocument doc : docs) {
            List<KnowledgeChunk> chunks = chunkMapper.selectByDocId(doc.getId());
            List<String> vectorIds = chunks.stream()
                    .map(KnowledgeChunk::getVectorId)
                    .collect(Collectors.toList());
            if (!vectorIds.isEmpty()) {
                try {
                    qdrantService.deletePoints(kb.getKbCode(), vectorIds);
                } catch (Exception e) {
                    log.error("删除向量数据失败", e);
                }
            }
        }

        try {
            qdrantService.deleteCollection(kb.getKbCode());
        } catch (Exception e) {
            log.error("删除 Qdrant Collection 失败", e);
        }

        kbMapper.deleteById(id);
    }

    @Transactional
    public KnowledgeDocumentVO uploadDocument(Long kbId, MultipartFile file, String fileUrl, String fileKey, Long userId) {
        KnowledgeBase kb = kbMapper.selectById(kbId);
        if (kb == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "知识库不存在");
        }
        if (!kb.getCreatedBy().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "无权访问该知识库");
        }

        // Step 1: Save document record with status "PENDING"
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setKbId(kbId);
        doc.setDocName(file.getOriginalFilename());
        doc.setDocType(file.getContentType());
        doc.setFileUrl(fileUrl);
        doc.setFileKey(fileKey);
        doc.setFileSize(file.getSize());
        doc.setChunkCount(0);
        doc.setVectorStatus(KnowledgeDocStatus.PENDING.getCode());
        doc.setStatus(ACTIVE_STATUS);
        docMapper.insert(doc);

        // Step 2: Async process document (parse, chunk, embed, store)
        processDocumentAsync(doc.getId(), kbId, file, kb.getKbCode());

        return convertToDocVO(doc);
    }

    @Async("documentTaskExecutor")
    public void processDocumentAsync(Long docId, Long kbId, MultipartFile file, String kbCode) {
        try {
            // Update status to PROCESSING
            updateDocumentStatus(docId, KnowledgeDocStatus.PROCESSING, null);

            // Parse document
            String content = documentParser.parse(file);
            List<String> chunks = documentChunker.chunk(content, null, null);

            // Update chunk count
            KnowledgeDocument doc = docMapper.selectById(docId);
            doc.setChunkCount(chunks.size());
            docMapper.updateById(doc);

            // Update status to EMBEDDING
            updateDocumentStatus(docId, KnowledgeDocStatus.EMBEDDING, null);

            // Batch embed all chunks
            List<List<Float>> embeddings = embeddingService.embedBatch(chunks);

            // Prepare Qdrant points and chunk entities
            List<Points.PointStruct> pointStructs = new ArrayList<>();
            List<KnowledgeChunk> chunkEntities = new ArrayList<>();

            for (int i = 0; i < chunks.size(); i++) {
                String chunkContent = chunks.get(i);
                String vectorId = UUID.randomUUID().toString();
                List<Float> vector = embeddings.get(i);

                Points.PointStruct point = buildPointStruct(docId, i, chunkContent, vectorId, vector);
                pointStructs.add(point);

                KnowledgeChunk chunk = buildChunkEntity(docId, kbId, i, chunkContent, vectorId);
                chunkEntities.add(chunk);
            }

            // Store in Qdrant
            qdrantService.upsertPoints(kbCode, pointStructs);

            // Save chunks to database
            for (KnowledgeChunk chunk : chunkEntities) {
                chunkMapper.insert(chunk);
            }

            // Update status to COMPLETED
            updateDocumentStatus(docId, KnowledgeDocStatus.COMPLETED, null);

            // Update knowledge base document count
            incrementDocumentCount(kbId);

            log.info("Document {} processed successfully: {} chunks", docId, chunks.size());

        } catch (Exception e) {
            log.error("Document processing failed: docId={}", docId, e);
            updateDocumentStatus(docId, KnowledgeDocStatus.FAILED, e.getMessage());
        }
    }

    private Points.PointStruct buildPointStruct(Long docId, int index, String chunkContent, String vectorId, List<Float> vector) {
        return Points.PointStruct.newBuilder()
                .setId(Points.PointId.newBuilder().setUuid(vectorId).build())
                .setVectors(Points.Vectors.newBuilder()
                        .setVector(Points.Vector.newBuilder().addAllData(vector).build())
                        .build())
                .putPayload(PAYLOAD_CONTENT, JsonWithInt.Value.newBuilder()
                        .setStringValue(chunkContent).build())
                .putPayload(PAYLOAD_DOC_ID, JsonWithInt.Value.newBuilder()
                        .setStringValue(String.valueOf(docId)).build())
                .putPayload(PAYLOAD_CHUNK_INDEX, JsonWithInt.Value.newBuilder()
                        .setIntegerValue(index).build())
                .build();
    }

    private KnowledgeChunk buildChunkEntity(Long docId, Long kbId, int index, String chunkContent, String vectorId) {
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setDocId(docId);
        chunk.setKbId(kbId);
        chunk.setChunkId(CHUNK_ID_PREFIX + docId + UNDERLINE + index);
        chunk.setContent(chunkContent);
        chunk.setVectorId(vectorId);
        chunk.setChunkIndex(index);
        chunk.setStatus(ACTIVE_STATUS);
        return chunk;
    }

    private void updateDocumentStatus(Long docId, KnowledgeDocStatus status, String errorMessage) {
        KnowledgeDocument doc = docMapper.selectById(docId);
        if (doc != null) {
            doc.setVectorStatus(status.getCode());
            doc.setErrorMessage(errorMessage);
            docMapper.updateById(doc);
        }
    }

    private void incrementDocumentCount(Long kbId) {
        kbMapper.incrementDocumentCount(kbId);
    }

    public List<KnowledgeDocumentVO> listDocuments(Long kbId, Long userId) {
        KnowledgeBase kb = kbMapper.selectById(kbId);
        if (kb == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "知识库不存在");
        }
        if (!kb.getCreatedBy().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "无权访问该知识库");
        }
        return docMapper.selectByKbId(kbId).stream()
                .map(this::convertToDocVO)
                .collect(Collectors.toList());
    }

    private KnowledgeBaseVO convertToBaseVO(KnowledgeBase kb) {
        KnowledgeBaseVO vo = new KnowledgeBaseVO();
        vo.setId(kb.getId());
        vo.setKbName(kb.getKbName());
        vo.setKbCode(kb.getKbCode());
        vo.setDescription(kb.getDescription());
        vo.setEmbeddingModel(kb.getEmbeddingModel());
        vo.setDocumentCount(kb.getDocumentCount());
        vo.setStatus(kb.getStatus());
        vo.setCreatedAt(kb.getCreatedAt());
        vo.setUpdatedAt(kb.getUpdatedAt());
        return vo;
    }

    private KnowledgeDocumentVO convertToDocVO(KnowledgeDocument doc) {
        KnowledgeDocumentVO vo = new KnowledgeDocumentVO();
        vo.setId(doc.getId());
        vo.setKbId(doc.getKbId());
        vo.setDocName(doc.getDocName());
        vo.setDocType(doc.getDocType());
        vo.setFileUrl(doc.getFileUrl());
        vo.setFileSize(doc.getFileSize());
        vo.setChunkCount(doc.getChunkCount());
        vo.setVectorStatus(doc.getVectorStatus());
        vo.setErrorMessage(doc.getErrorMessage());
        vo.setStatus(doc.getStatus());
        vo.setCreatedAt(doc.getCreatedAt());
        return vo;
    }
}
