package com.agent.knowledge.rag;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.knowledge.embedding.EmbeddingService;
import com.agent.knowledge.vector.QdrantService;
import io.qdrant.client.grpc.Points;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RAGService {

    private final QdrantService qdrantService;
    private final EmbeddingService embeddingService;

    @Value("${rag.similarity-threshold:0.5}")
    private float similarityThreshold;

    public List<RetrievalResult> retrieve(String kbCode, String query, int topK) {
        List<Float> queryVector = embeddingService.embed(query);
        try {
            List<Points.ScoredPoint> results = qdrantService.search(kbCode, queryVector, topK);
            return results.stream()
                    .filter(point -> point.getScore() >= similarityThreshold)
                    .map(point -> new RetrievalResult(
                            point.getId().getUuid(),
                            point.getPayloadMap().get("content").getStringValue(),
                            point.getPayloadMap().get("doc_id").getStringValue(),
                            point.getScore()
                    ))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("RAG 检索失败: kbCode={}, query={}", kbCode, query, e);
            throw new BusinessException(ErrorCode.VECTOR_SERVICE_ERROR.getCode(), "检索失败: " + e.getMessage());
        }
    }

    public String buildContext(List<RetrievalResult> results) {
        StringBuilder context = new StringBuilder();
        context.append("以下是与用户问题相关的参考信息:\n\n");
        for (int i = 0; i < results.size(); i++) {
            RetrievalResult result = results.get(i);
            context.append("[参考 ").append(i + 1).append("] (相关度: ")
                    .append(String.format("%.2f", result.getScore())).append(")\n");
            context.append(result.getContent()).append("\n\n");
        }
        return context.toString();
    }

    public String buildPrompt(String userQuery, String context, String systemPrompt) {
        StringBuilder prompt = new StringBuilder();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            prompt.append(systemPrompt).append("\n\n");
        }
        prompt.append(context);
        prompt.append("用户问题: ").append(userQuery).append("\n\n");
        prompt.append("请基于以上参考信息回答用户问题。如果参考信息不足以回答问题，请明确说明。");
        return prompt.toString();
    }

    public static class RetrievalResult {
        private final String chunkId;
        private final String content;
        private final String docId;
        private final float score;

        public RetrievalResult(String chunkId, String content, String docId, float score) {
            this.chunkId = chunkId;
            this.content = content;
            this.docId = docId;
            this.score = score;
        }

        public String getChunkId() { return chunkId; }
        public String getContent() { return content; }
        public String getDocId() { return docId; }
        public float getScore() { return score; }
    }
}
