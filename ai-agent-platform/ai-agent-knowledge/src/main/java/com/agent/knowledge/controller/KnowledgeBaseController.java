package com.agent.knowledge.controller;

import com.agent.common.result.Result;
import com.agent.knowledge.rag.RAGService;
import com.agent.knowledge.service.KnowledgeBaseService;
import com.agent.knowledge.vo.KnowledgeBaseVO;
import com.agent.knowledge.vo.KnowledgeDocumentVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/knowledge-bases")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;
    private final RAGService ragService;

    @PostMapping
    public Result<KnowledgeBaseVO> createKnowledgeBase(
            @Valid @RequestBody CreateKBRequest request,
            @RequestHeader("X-User-Id") Long userId) {
        return Result.success(knowledgeBaseService.createKnowledgeBase(
                request.getKbName(), request.getKbCode(),
                request.getDescription(), request.getEmbeddingModel(), userId));
    }

    @GetMapping
    public Result<List<KnowledgeBaseVO>> listKnowledgeBases(
            @RequestHeader("X-User-Id") Long userId) {
        return Result.success(knowledgeBaseService.listKnowledgeBases(userId));
    }

    @GetMapping("/{id}")
    public Result<KnowledgeBaseVO> getKnowledgeBase(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId) {
        return Result.success(knowledgeBaseService.getKnowledgeBase(id, userId));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteKnowledgeBase(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId) {
        knowledgeBaseService.deleteKnowledgeBase(id, userId);
        return Result.success();
    }

    @PostMapping("/{kbId}/documents")
    public Result<KnowledgeDocumentVO> uploadDocument(
            @PathVariable Long kbId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "fileUrl", required = false) String fileUrl,
            @RequestParam(value = "fileKey", required = false) String fileKey,
            @RequestHeader("X-User-Id") Long userId) {
        return Result.success(knowledgeBaseService.uploadDocument(kbId, file, fileUrl, fileKey, userId));
    }

    @GetMapping("/{kbId}/documents")
    public Result<List<KnowledgeDocumentVO>> listDocuments(
            @PathVariable Long kbId,
            @RequestHeader("X-User-Id") Long userId) {
        return Result.success(knowledgeBaseService.listDocuments(kbId, userId));
    }

    @PostMapping("/{kbCode}/retrieve")
    public Result<List<RAGService.RetrievalResult>> retrieve(
            @PathVariable String kbCode,
            @Valid @RequestBody RetrieveRequest request) {
        return Result.success(ragService.retrieve(kbCode, request.getQuery(), request.getTopK() != null ? request.getTopK() : 5));
    }

    @PostMapping("/{kbCode}/rag")
    public Result<RAGResponse> ragQuery(
            @PathVariable String kbCode,
            @Valid @RequestBody RAGQueryRequest request) {
        List<RAGService.RetrievalResult> results = ragService.retrieve(kbCode, request.getQuery(),
                request.getTopK() != null ? request.getTopK() : 5);
        String context = ragService.buildContext(results);
        String prompt = ragService.buildPrompt(request.getQuery(), context, request.getSystemPrompt());

        RAGResponse response = new RAGResponse();
        response.setPrompt(prompt);
        response.setContext(context);
        response.setReferences(results);
        return Result.success(response);
    }

    @lombok.Data
    public static class CreateKBRequest {
        @NotBlank private String kbName;
        @NotBlank private String kbCode;
        private String description;
        private String embeddingModel;
    }

    @lombok.Data
    public static class RetrieveRequest {
        @NotBlank private String query;
        private Integer topK;
    }

    @lombok.Data
    public static class RAGQueryRequest {
        @NotBlank private String query;
        private String systemPrompt;
        private Integer topK;
    }

    @lombok.Data
    public static class RAGResponse {
        private String prompt;
        private String context;
        private List<RAGService.RetrievalResult> references;
    }
}
