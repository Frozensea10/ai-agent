package com.agent.knowledge.service;

import com.agent.knowledge.chunk.DocumentChunker;
import com.agent.knowledge.embedding.EmbeddingService;
import com.agent.knowledge.entity.KnowledgeChunk;
import com.agent.knowledge.entity.KnowledgeDocument;
import com.agent.knowledge.mapper.KnowledgeBaseMapper;
import com.agent.knowledge.mapper.KnowledgeChunkMapper;
import com.agent.knowledge.mapper.KnowledgeDocumentMapper;
import com.agent.knowledge.parser.DocumentParser;
import com.agent.knowledge.vector.QdrantService;
import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.grpc.Points;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.agent.knowledge.service.KnowledgeBaseConstants.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentAsyncProcessorImpl implements DocumentAsyncProcessor {

    private final KnowledgeDocumentMapper docMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final KnowledgeBaseMapper kbMapper;
    private final DocumentParser documentParser;
    private final DocumentChunker documentChunker;
    private final EmbeddingService embeddingService;
    private final QdrantService qdrantService;

    @Override
    @Async("documentTaskExecutor")
    public void processDocumentAsync(Long docId, Long kbId, byte[] fileContent, String originalFilename,
                                     String contentType, String kbCode, String embeddingProvider,
                                     String embeddingModel) {
        try {
            // Update status to PROCESSING
            updateDocumentStatus(docId, KnowledgeDocStatus.PROCESSING, null);

            // Parse document (使用 byte[] 重建 MultipartFile，避免依赖原始请求的临时文件)
            MultipartFile file = new ByteArrayMultipartFile(fileContent, originalFilename, contentType);
            String content = documentParser.parse(file);
            List<String> chunks = documentChunker.chunk(content, null, null);

            // Update chunk count
            KnowledgeDocument doc = docMapper.selectById(docId);
            if (doc == null) {
                throw new IllegalStateException("文档不存在: docId=" + docId);
            }
            doc.setChunkCount(chunks.size());
            docMapper.updateById(doc);

            // Update status to EMBEDDING
            updateDocumentStatus(docId, KnowledgeDocStatus.EMBEDDING, null);

            // Batch embed all chunks
            List<List<Float>> embeddings = embeddingService.embedBatch(chunks, embeddingProvider, embeddingModel);

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
            // 确保 collection 存在：若知识库创建后 collection 被删除/未创建，此处自动按当前维度重建
            int vectorSize = embeddingService.getVectorSize(embeddingProvider, embeddingModel);
            try {
                qdrantService.createCollection(kbCode, vectorSize);
            } catch (Exception e) {
                log.warn("确保 Qdrant collection 存在时异常（可能已存在），继续尝试 upsert: {}", e.getMessage());
            }
            qdrantService.upsertPoints(kbCode, pointStructs);

            // Save chunks to database
            // 注：当前逐条 insert，未使用批量插入。KnowledgeChunkMapper 继承 BaseMapper
            // 而非 BatchBaseMapper，且无对应 IService，改造成本较高，暂保留单条插入。
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
            String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            updateDocumentStatus(docId, KnowledgeDocStatus.FAILED, errorMsg);
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

    /**
     * 基于 {@code byte[]} 的 {@link MultipartFile} 简单实现，用于在异步线程中
     * 向 {@link DocumentParser} 提供解析所需内容，避免依赖已被清理的请求临时文件。
     */
    private static class ByteArrayMultipartFile implements MultipartFile {

        private final byte[] content;
        private final String name;
        private final String originalFilename;
        private final String contentType;

        ByteArrayMultipartFile(byte[] content, String originalFilename, String contentType) {
            this.content = content != null ? content : new byte[0];
            this.name = originalFilename;
            this.originalFilename = originalFilename;
            this.contentType = contentType;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return content.length == 0;
        }

        @Override
        public long getSize() {
            return content.length;
        }

        @Override
        public byte[] getBytes() {
            return content;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
            Files.write(dest.toPath(), content);
        }
    }
}
