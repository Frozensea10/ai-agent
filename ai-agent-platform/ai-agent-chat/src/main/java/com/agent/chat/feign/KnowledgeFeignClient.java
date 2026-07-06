package com.agent.chat.feign;

import com.agent.common.result.Result;
import lombok.Data;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

@FeignClient(name = "ai-agent-knowledge", url = "${feign.client.url.knowledge-service:http://localhost:8084}", path = "/api/v1/knowledge-bases", fallbackFactory = KnowledgeFeignClientFallbackFactory.class)
public interface KnowledgeFeignClient {

    @GetMapping("/{id}")
    Result<KnowledgeBaseVO> getKnowledgeBaseById(@PathVariable("id") Long id, @RequestHeader("X-User-Id") Long userId);

    @PostMapping("/{kbCode}/retrieve")
    Result<List<RetrievalResult>> retrieve(
            @PathVariable("kbCode") String kbCode,
            @RequestBody RetrieveRequest request);

    @PostMapping("/{kbCode}/rag")
    Result<RAGResponse> ragQuery(
            @PathVariable("kbCode") String kbCode,
            @RequestBody RAGQueryRequest request,
            @RequestHeader("X-User-Id") Long userId);

    @Data
    class RetrieveRequest {
        private String query;
        private Integer topK;
    }

    @Data
    class RAGQueryRequest {
        private String query;
        private String systemPrompt;
        private Integer topK;
    }

    @Data
    class RAGResponse {
        private String prompt;
        private String context;
        private List<RetrievalResult> references;
    }

    @Data
    class KnowledgeBaseVO {
        private Long id;
        private String kbName;
        private String kbCode;
        private String description;
        private String embeddingModel;
        private String embeddingProvider;
        private Integer documentCount;
        private Integer status;
    }

    @Data
    class RetrievalResult {
        private String chunkId;
        private String content;
        private String docId;
        private float score;
    }
}
