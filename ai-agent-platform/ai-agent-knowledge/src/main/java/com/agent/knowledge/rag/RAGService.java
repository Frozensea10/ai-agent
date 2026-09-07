package com.agent.knowledge.rag;

import lombok.Data;

import java.util.List;

public interface RAGService {

    /**
     * 检索知识库相关文档片段：校验知识库权限后执行向量检索。
     *
     * @param kbCode 知识库编码
     * @param query 查询文本
     * @param topK 返回的最大结果数，为 null 时使用默认值
     * @param userId 当前用户 ID（用于权限校验）
     * @return 过滤后的检索结果列表
     */
    List<RetrievalResult> retrieve(String kbCode, String query, Integer topK, Long userId);

    /**
     * RAG 查询编排：检索相关片段、构建上下文与增强提示词，并拼装查询结果。
     *
     * @param kbCode 知识库编码
     * @param query 查询文本
     * @param topK 返回的最大结果数，为 null 时使用默认值
     * @param systemPrompt 系统提示词（可选）
     * @param userId 当前用户 ID（用于权限校验）
     * @return RAG 查询结果，包含增强提示词、参考上下文与检索引用
     */
    RAGQueryResult ragQuery(String kbCode, String query, Integer topK, String systemPrompt, Long userId);

    List<RetrievalResult> retrieve(String kbCode, String query, int topK, String provider, String modelName);

    /**
     * 将检索结果拼接为带序号与相关度标注的参考上下文文本。
     *
     * @param results 检索结果列表
     * @return 供大模型参考的上下文文本
     */
    String buildContext(List<RetrievalResult> results);

    String buildPrompt(String userQuery, String context, String systemPrompt);

    /**
     * 单条检索结果，包含向量点 ID、分块内容、所属文档 ID 及相似度得分。
     */
    @Data
    class RetrievalResult {
        /** 向量点 ID（分块对应的 UUID） */
        private final String chunkId;
        /** 分块文本内容 */
        private final String content;
        /** 所属文档 ID */
        private final String docId;
        /** 相似度得分（余弦相似度） */
        private final float score;

        public RetrievalResult(String chunkId, String content, String docId, float score) {
            this.chunkId = chunkId;
            this.content = content;
            this.docId = docId;
            this.score = score;
        }
    }

    /**
     * RAG 查询结果，包含增强提示词、参考上下文与检索引用。
     */
    @Data
    class RAGQueryResult {
        /** 构建的完整增强提示词 */
        private String prompt;
        /** 检索得到的参考上下文文本 */
        private String context;
        /** 检索引用列表 */
        private List<RetrievalResult> references;
    }
}
