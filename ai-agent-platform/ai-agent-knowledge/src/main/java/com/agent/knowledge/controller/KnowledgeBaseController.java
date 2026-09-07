package com.agent.knowledge.controller;

import com.agent.common.constant.CommonConstants;
import com.agent.common.result.Result;
import com.agent.knowledge.rag.RAGService;
import com.agent.knowledge.service.KnowledgeBaseService;
import com.agent.knowledge.vo.KnowledgeBaseVO;
import com.agent.knowledge.vo.KnowledgeDocumentVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "知识库管理", description = "知识库、文档上传与 RAG 检索接口")
@Slf4j
@RestController
@RequestMapping("/api/v1/knowledge-bases")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    /** 知识库业务服务 */
    private final KnowledgeBaseService knowledgeBaseService;
    /** RAG 检索服务 */
    private final RAGService ragService;

    @Operation(summary = "创建知识库", description = "为当前用户创建新的知识库")
    @PostMapping
    public Result<KnowledgeBaseVO> createKnowledgeBase(
            @Valid @RequestBody CreateKBRequest request,
            @RequestHeader(CommonConstants.HEADER_USER_ID) Long userId) {
        return Result.success(knowledgeBaseService.createKnowledgeBase(
                request.getKbName(), request.getKbCode(),
                request.getDescription(), request.getEmbeddingModel(),
                request.getEmbeddingProvider(), userId));
    }

    @Operation(summary = "查询知识库列表", description = "查询当前用户的所有知识库")
    @GetMapping
    public Result<List<KnowledgeBaseVO>> listKnowledgeBases(
            @RequestHeader(CommonConstants.HEADER_USER_ID) Long userId) {
        return Result.success(knowledgeBaseService.listKnowledgeBases(userId));
    }

    @Operation(summary = "查询知识库详情", description = "根据知识库 ID 查询详情")
    @GetMapping("/{id}")
    public Result<KnowledgeBaseVO> getKnowledgeBase(
            @PathVariable Long id,
            @RequestHeader(CommonConstants.HEADER_USER_ID) Long userId) {
        return Result.success(knowledgeBaseService.getKnowledgeBase(id, userId));
    }

    @Operation(summary = "更新知识库", description = "根据知识库 ID 更新名称、描述与嵌入模型")
    @PutMapping("/{id}")
    public Result<KnowledgeBaseVO> updateKnowledgeBase(
            @PathVariable Long id,
            @Valid @RequestBody UpdateKBRequest request,
            @RequestHeader(CommonConstants.HEADER_USER_ID) Long userId) {
        return Result.success(knowledgeBaseService.updateKnowledgeBase(
                id, request.getKbName(), request.getDescription(),
                request.getEmbeddingModel(), request.getEmbeddingProvider(), userId));
    }

    @Operation(summary = "删除知识库", description = "根据知识库 ID 删除知识库及其文档")
    @DeleteMapping("/{id}")
    public Result<Void> deleteKnowledgeBase(
            @PathVariable Long id,
            @RequestHeader(CommonConstants.HEADER_USER_ID) Long userId) {
        knowledgeBaseService.deleteKnowledgeBase(id, userId);
        return Result.success();
    }

    @Operation(summary = "上传文档", description = "向指定知识库上传文档并进行向量化解析")
    @PostMapping("/{kbId}/documents")
    public Result<KnowledgeDocumentVO> uploadDocument(
            @PathVariable Long kbId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "fileUrl", required = false) String fileUrl,
            @RequestParam(value = "fileKey", required = false) String fileKey,
            @RequestHeader(CommonConstants.HEADER_USER_ID) Long userId) {
        return Result.success(knowledgeBaseService.uploadDocument(kbId, file, fileUrl, fileKey, userId));
    }

    @Operation(summary = "查询文档列表", description = "查询指定知识库下的所有文档")
    @GetMapping("/{kbId}/documents")
    public Result<List<KnowledgeDocumentVO>> listDocuments(
            @PathVariable Long kbId,
            @RequestHeader(CommonConstants.HEADER_USER_ID) Long userId) {
        return Result.success(knowledgeBaseService.listDocuments(kbId, userId));
    }

    @Operation(summary = "检索文档片段", description = "根据查询文本在指定知识库中检索相关片段")
    @PostMapping("/{kbCode}/retrieve")
    public Result<List<RAGService.RetrievalResult>> retrieve(
            @PathVariable String kbCode,
            @Valid @RequestBody RetrieveRequest request,
            @RequestHeader(CommonConstants.HEADER_USER_ID) Long userId) {
        return Result.success(ragService.retrieve(kbCode, request.getQuery(), request.getTopK(), userId));
    }

    @Operation(summary = "RAG 查询", description = "在指定知识库中检索并构建增强提示词")
    @PostMapping("/{kbCode}/rag")
    public Result<RAGService.RAGQueryResult> ragQuery(
            @PathVariable String kbCode,
            @Valid @RequestBody RAGQueryRequest request,
            @RequestHeader(CommonConstants.HEADER_USER_ID) Long userId) {
        return Result.success(ragService.ragQuery(kbCode, request.getQuery(),
                request.getTopK(), request.getSystemPrompt(), userId));
    }

    /**
     * 更新知识库请求体。
     */
    @lombok.Data
    public static class UpdateKBRequest {
        /** 知识库名称 */
        private String kbName;
        /** 知识库描述 */
        private String description;
        /** Embedding 模型名称 */
        private String embeddingModel;
        /** Embedding 提供商标识 */
        private String embeddingProvider;
    }

    /**
     * 创建知识库请求体。
     */
    @lombok.Data
    public static class CreateKBRequest {
        /** 知识库名称（必填） */
        @NotBlank private String kbName;
        /** 知识库编码（必填，唯一） */
        @NotBlank private String kbCode;
        /** 知识库描述 */
        private String description;
        /** Embedding 模型名称 */
        private String embeddingModel;
        /** Embedding 提供商标识 */
        private String embeddingProvider;
    }

    /**
     * 文档片段检索请求体。
     */
    @lombok.Data
    public static class RetrieveRequest {
        /** 查询文本（必填） */
        @NotBlank private String query;
        /** 返回的最大结果数，默认 10，最大 20 */
        @Max(value = 20, message = "topK 不能超过 20") private Integer topK;
    }

    /**
     * RAG 查询请求体。
     */
    @lombok.Data
    public static class RAGQueryRequest {
        /** 查询文本（必填） */
        @NotBlank private String query;
        /** 系统提示词（可选） */
        private String systemPrompt;
        /** 返回的最大结果数，默认 10，最大 20 */
        @Max(value = 20, message = "topK 不能超过 20") private Integer topK;
    }
}
