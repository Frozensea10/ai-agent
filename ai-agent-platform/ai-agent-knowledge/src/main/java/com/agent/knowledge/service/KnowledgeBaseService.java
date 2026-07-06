package com.agent.knowledge.service;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.knowledge.embedding.EmbeddingService;
import com.agent.knowledge.entity.KnowledgeBase;
import com.agent.knowledge.entity.KnowledgeChunk;
import com.agent.knowledge.entity.KnowledgeDocument;
import com.agent.knowledge.mapper.KnowledgeBaseMapper;
import com.agent.knowledge.mapper.KnowledgeChunkMapper;
import com.agent.knowledge.mapper.KnowledgeDocumentMapper;
import com.agent.knowledge.vector.QdrantService;
import com.agent.knowledge.vo.KnowledgeBaseVO;
import com.agent.knowledge.vo.KnowledgeDocumentVO;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static com.agent.knowledge.service.KnowledgeBaseConstants.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {

    private final KnowledgeBaseMapper kbMapper;
    private final KnowledgeDocumentMapper docMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final EmbeddingService embeddingService;
    private final QdrantService qdrantService;
    private final DocumentAsyncProcessor documentAsyncProcessor;

    @Transactional
    public KnowledgeBaseVO createKnowledgeBase(String kbName, String kbCode, String description, String embeddingModel, String embeddingProvider, Long userId) {
        KnowledgeBase existing = kbMapper.selectByCode(kbCode);
        if (existing != null) {
            // kb_code 有唯一索引，即使软删记录也会冲突。若存在软删记录则物理删除后重建，避免唯一键冲突。
            if (existing.getDeleted() != null && existing.getDeleted() == 1) {
                kbMapper.physicalDeleteById(existing.getId());
                // 同时清理 Qdrant 中可能残留的旧 collection
                try {
                    qdrantService.deleteCollection(kbCode);
                } catch (Exception e) {
                    log.warn("清理旧 Qdrant collection 失败（可忽略）: {}", e.getMessage());
                }
            } else {
                throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "知识库编码已存在");
            }
        }

        KnowledgeBase kb = new KnowledgeBase();
        kb.setKbName(kbName);
        kb.setKbCode(kbCode);
        kb.setDescription(description);
        kb.setEmbeddingModel(embeddingModel != null ? embeddingModel : DEFAULT_EMBEDDING_MODEL);
        kb.setEmbeddingProvider(embeddingProvider != null ? embeddingProvider : "openai");
        kb.setDocumentCount(0);
        kb.setStatus(ACTIVE_STATUS);
        kb.setCreatedBy(userId);
        kbMapper.insert(kb);

        try {
            qdrantService.createCollection(kbCode, embeddingService.getVectorSize(kb.getEmbeddingProvider(), kb.getEmbeddingModel()));
        } catch (Exception e) {
            log.error("创建 Qdrant Collection 失败", e);
            throw new BusinessException(ErrorCode.VECTOR_SERVICE_ERROR.getCode(), "向量数据库初始化失败: " + e.getMessage());
        }

        return convertToBaseVO(kb);
    }

    @Transactional
    public KnowledgeBaseVO updateKnowledgeBase(Long id, String kbName, String description, String embeddingModel, String embeddingProvider, Long userId) {
        KnowledgeBase kb = kbMapper.selectById(id);
        if (kb == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "知识库不存在");
        }
        if (!userId.equals(kb.getCreatedBy())) {
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
        if (embeddingProvider != null) {
            kb.setEmbeddingProvider(embeddingProvider);
        }
        kbMapper.updateById(kb);
        return convertToBaseVO(kb);
    }

    public KnowledgeBaseVO getKnowledgeBaseByCode(String kbCode, Long userId) {
        KnowledgeBase kb = kbMapper.selectByCode(kbCode);
        if (kb == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "知识库不存在");
        }
        if (!userId.equals(kb.getCreatedBy())) {
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
        if (!userId.equals(kb.getCreatedBy())) {
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
        if (!userId.equals(kb.getCreatedBy())) {
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

        // 清理 DB 关联数据（chunk -> document -> knowledge_base），避免数据孤儿
        chunkMapper.delete(new QueryWrapper<KnowledgeChunk>().eq("kb_id", id));
        docMapper.delete(new QueryWrapper<KnowledgeDocument>().eq("kb_id", id));
        kbMapper.deleteById(id);

        // Qdrant collection 删除为外部调用，置于最后执行（DB 已提交后），
        // 避免先删除向量后 DB 回滚导致状态不一致
        try {
            qdrantService.deleteCollection(kb.getKbCode());
        } catch (Exception e) {
            log.error("删除 Qdrant Collection 失败", e);
        }
    }

    @Transactional
    public KnowledgeDocumentVO uploadDocument(Long kbId, MultipartFile file, String fileUrl, String fileKey, Long userId) {
        KnowledgeBase kb = kbMapper.selectById(kbId);
        if (kb == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "知识库不存在");
        }
        if (!userId.equals(kb.getCreatedBy())) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "无权访问该知识库");
        }

        String originalFilename = file.getOriginalFilename();
        String contentType = file.getContentType();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "文件名不能为空");
        }

        // Step 1: Save document record with status "PENDING"
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setKbId(kbId);
        doc.setDocName(originalFilename);
        doc.setDocType(contentType);
        doc.setFileUrl(fileUrl);
        doc.setFileKey(fileKey);
        doc.setFileSize(file.getSize());
        doc.setChunkCount(0);
        doc.setVectorStatus(KnowledgeDocStatus.PENDING.getCode());
        doc.setStatus(ACTIVE_STATUS);
        docMapper.insert(doc);

        // Step 2: 在同步阶段读取 MultipartFile 内容为 byte[]，避免将 MultipartFile
        // 传入异步线程导致请求结束后临时文件被清理、InputStream 失效的问题
        byte[] fileContent;
        try {
            fileContent = file.getBytes();
        } catch (IOException e) {
            log.error("读取上传文件内容失败: kbId={}", kbId, e);
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "文件内容读取失败");
        }

        // 通过独立 Bean 调用异步方法，使 Spring AOP 代理正确拦截 @Async 注解
        documentAsyncProcessor.processDocumentAsync(doc.getId(), kbId, fileContent, originalFilename,
                contentType, kb.getKbCode(), kb.getEmbeddingProvider(), kb.getEmbeddingModel());

        return convertToDocVO(doc);
    }

    public List<KnowledgeDocumentVO> listDocuments(Long kbId, Long userId) {
        KnowledgeBase kb = kbMapper.selectById(kbId);
        if (kb == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "知识库不存在");
        }
        if (!userId.equals(kb.getCreatedBy())) {
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
        vo.setEmbeddingProvider(kb.getEmbeddingProvider());
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
