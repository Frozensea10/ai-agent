package com.agent.knowledge.rag;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.knowledge.embedding.EmbeddingService;
import com.agent.knowledge.service.KnowledgeBaseConstants;
import com.agent.knowledge.service.KnowledgeBaseService;
import com.agent.knowledge.vector.QdrantService;
import com.agent.knowledge.vo.KnowledgeBaseVO;
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
public class RAGServiceImpl implements RAGService {

    /** RAG 检索默认返回条数。 */
    private static final int DEFAULT_TOP_K = 10;

    private final QdrantService qdrantService;
    private final EmbeddingService embeddingService;
    private final KnowledgeBaseService knowledgeBaseService;

    @Value("${rag.similarity-threshold:0.5}")
    private float similarityThreshold;

    @Override
    public List<RetrievalResult> retrieve(String kbCode, String query, Integer topK, Long userId) {
        KnowledgeBaseVO kb = knowledgeBaseService.getKnowledgeBaseByCode(kbCode, userId);
        return retrieve(kbCode, query, resolveTopK(topK), kb.getEmbeddingProvider(), kb.getEmbeddingModel());
    }

    @Override
    public RAGQueryResult ragQuery(String kbCode, String query, Integer topK, String systemPrompt, Long userId) {
        KnowledgeBaseVO kb = knowledgeBaseService.getKnowledgeBaseByCode(kbCode, userId);
        List<RetrievalResult> results = retrieve(kbCode, query, resolveTopK(topK),
                kb.getEmbeddingProvider(), kb.getEmbeddingModel());
        String context = buildContext(results);
        String prompt = buildPrompt(query, context, systemPrompt);

        RAGQueryResult result = new RAGQueryResult();
        result.setPrompt(prompt);
        result.setContext(context);
        result.setReferences(results);
        return result;
    }

    @Override
    public List<RetrievalResult> retrieve(String kbCode, String query, int topK, String provider, String modelName) {
        log.info("RAG 检索开始: kbCode={}, query={}, provider={}, modelName={}, topK={}, threshold={}",
                kbCode, query, provider, modelName, topK, similarityThreshold);
        List<Float> queryVector = embeddingService.embed(query, provider, modelName);
        try {
            List<Points.ScoredPoint> results = qdrantService.search(kbCode, queryVector, topK);
            log.info("RAG 向量检索原始结果: kbCode={}, 返回points数={}", kbCode, results.size());
            for (Points.ScoredPoint point : results) {
                log.info("RAG 原始point: id={}, score={}, hasContent={}, hasDocId={}",
                        point.getId().getUuid(),
                        point.getScore(),
                        point.getPayloadMap().containsKey(KnowledgeBaseConstants.PAYLOAD_CONTENT),
                        point.getPayloadMap().containsKey(KnowledgeBaseConstants.PAYLOAD_DOC_ID));
            }
            List<RetrievalResult> filtered = results.stream()
                    .filter(point -> point.getScore() >= similarityThreshold)
                    .filter(point -> point.getPayloadMap().get(KnowledgeBaseConstants.PAYLOAD_CONTENT) != null
                            && point.getPayloadMap().get(KnowledgeBaseConstants.PAYLOAD_DOC_ID) != null)
                    .map(point -> new RetrievalResult(
                            point.getId().getUuid(),
                            point.getPayloadMap().get(KnowledgeBaseConstants.PAYLOAD_CONTENT).getStringValue(),
                            point.getPayloadMap().get(KnowledgeBaseConstants.PAYLOAD_DOC_ID).getStringValue(),
                            point.getScore()
                    ))
                    .collect(Collectors.toList());
            log.info("RAG 检索过滤后结果: kbCode={}, 召回数={}", kbCode, filtered.size());
            return filtered;
        } catch (Exception e) {
            log.error("RAG 检索失败: kbCode={}, queryLength={}", kbCode, query == null ? 0 : query.length(), e);
            throw new BusinessException(ErrorCode.VECTOR_SERVICE_ERROR.getCode(), "检索失败: " + e.getMessage());
        }
    }

    @Override
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

    @Override
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

    private int resolveTopK(Integer topK) {
        return topK != null ? topK : DEFAULT_TOP_K;
    }
}
