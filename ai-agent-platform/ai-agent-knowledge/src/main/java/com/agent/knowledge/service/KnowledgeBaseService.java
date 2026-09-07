package com.agent.knowledge.service;

import com.agent.knowledge.vo.KnowledgeBaseVO;
import com.agent.knowledge.vo.KnowledgeDocumentVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface KnowledgeBaseService {

    KnowledgeBaseVO createKnowledgeBase(String kbName, String kbCode, String description, String embeddingModel, String embeddingProvider, Long userId);

    KnowledgeBaseVO updateKnowledgeBase(Long id, String kbName, String description, String embeddingModel, String embeddingProvider, Long userId);

    KnowledgeBaseVO getKnowledgeBaseByCode(String kbCode, Long userId);

    List<KnowledgeBaseVO> listKnowledgeBases(Long userId);

    List<KnowledgeBaseVO> listAllKnowledgeBases();

    KnowledgeBaseVO getKnowledgeBase(Long id, Long userId);

    void deleteKnowledgeBase(Long id, Long userId);

    KnowledgeDocumentVO uploadDocument(Long kbId, MultipartFile file, String fileUrl, String fileKey, Long userId);

    List<KnowledgeDocumentVO> listDocuments(Long kbId, Long userId);
}
