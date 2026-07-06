package com.agent.knowledge.vector;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections;
import io.qdrant.client.grpc.Points;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
public class QdrantService {

    @Value("${qdrant.host:localhost}")
    private String host;

    @Value("${qdrant.grpc-port:6334}")
    private int grpcPort;

    @Value("${qdrant.collection-prefix:ai-agent}")
    private String collectionPrefix;

    @Value("${qdrant.timeout-seconds:30}")
    private long timeoutSeconds;

    private QdrantClient client;

    @PostConstruct
    public void init() {
        client = new QdrantClient(
                QdrantGrpcClient.newBuilder(host, grpcPort, false).build()
        );
        log.info("Qdrant client initialized: {}:{}", host, grpcPort);
    }

    @PreDestroy
    public void destroy() {
        if (client != null) {
            client.close();
            log.info("Qdrant client closed");
        }
    }

    public void createCollection(String collectionName, int vectorSize) throws ExecutionException, InterruptedException {
        String fullName = buildFullCollectionName(collectionName);
        try {
            client.getCollectionInfoAsync(fullName).get(timeoutSeconds, TimeUnit.SECONDS);
            log.info("Collection {} already exists", fullName);
            return;
        } catch (TimeoutException e) {
            log.error("获取 Qdrant Collection 信息超时: {}", fullName, e);
            throw new BusinessException(ErrorCode.VECTOR_SERVICE_ERROR.getCode(), "向量数据库操作超时");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof StatusRuntimeException sre
                    && sre.getStatus().getCode() == Status.Code.NOT_FOUND) {
                log.info("Creating collection: {}", fullName);
            } else {
                log.error("获取 Qdrant Collection 信息失败: {}", fullName, e);
                throw new BusinessException(ErrorCode.VECTOR_SERVICE_ERROR.getCode(), "向量数据库操作失败");
            }
        }

        try {
            client.createCollectionAsync(fullName,
                    Collections.VectorParams.newBuilder()
                            .setSize(vectorSize)
                            .setDistance(Collections.Distance.Cosine)
                            .build()
            ).get(timeoutSeconds, TimeUnit.SECONDS);
            log.info("Collection {} created successfully", fullName);
        } catch (TimeoutException e) {
            log.error("创建 Qdrant Collection 超时: {}", fullName, e);
            throw new BusinessException(ErrorCode.VECTOR_SERVICE_ERROR.getCode(), "向量数据库操作超时");
        }
    }

    public void upsertPoints(String collectionName, List<Points.PointStruct> points) throws ExecutionException, InterruptedException {
        String fullName = buildFullCollectionName(collectionName);
        try {
            client.upsertAsync(fullName, points).get(timeoutSeconds, TimeUnit.SECONDS);
            log.info("Upserted {} points to {}", points.size(), fullName);
        } catch (TimeoutException e) {
            log.error("写入 Qdrant 向量超时: {}", fullName, e);
            throw new BusinessException(ErrorCode.VECTOR_SERVICE_ERROR.getCode(), "向量数据库写入超时");
        }
    }

    public List<Points.ScoredPoint> search(String collectionName, List<Float> vector, int limit) throws ExecutionException, InterruptedException {
        String fullName = buildFullCollectionName(collectionName);
        try {
            List<Points.ScoredPoint> results = client.searchAsync(
                    Points.SearchPoints.newBuilder()
                            .setCollectionName(fullName)
                            .addAllVector(vector)
                            .setLimit(limit)
                            .setWithPayload(Points.WithPayloadSelector.newBuilder()
                                    .setEnable(true)
                                    .build())
                            .build()
            ).get(timeoutSeconds, TimeUnit.SECONDS);
            return results != null ? results : List.of();
        } catch (TimeoutException e) {
            log.error("查询 Qdrant 向量超时: {}", fullName, e);
            throw new BusinessException(ErrorCode.VECTOR_SERVICE_ERROR.getCode(), "向量数据库查询超时");
        }
    }

    public void deleteCollection(String collectionName) throws ExecutionException, InterruptedException {
        String fullName = buildFullCollectionName(collectionName);
        try {
            client.deleteCollectionAsync(fullName).get(timeoutSeconds, TimeUnit.SECONDS);
            log.info("Collection {} deleted", fullName);
        } catch (TimeoutException e) {
            log.error("删除 Qdrant Collection 超时: {}", fullName, e);
            throw new BusinessException(ErrorCode.VECTOR_SERVICE_ERROR.getCode(), "向量数据库操作超时");
        }
    }

    public void deletePoints(String collectionName, List<String> pointIds) throws ExecutionException, InterruptedException {
        String fullName = buildFullCollectionName(collectionName);
        List<Points.PointId> ids = pointIds.stream()
                .map(id -> Points.PointId.newBuilder().setUuid(id).build())
                .toList();
        try {
            client.deleteAsync(fullName, ids).get(timeoutSeconds, TimeUnit.SECONDS);
            log.info("Deleted {} points from {}", pointIds.size(), fullName);
        } catch (TimeoutException e) {
            log.error("删除 Qdrant 向量超时: {}", fullName, e);
            throw new BusinessException(ErrorCode.VECTOR_SERVICE_ERROR.getCode(), "向量数据库操作超时");
        }
    }

    private String buildFullCollectionName(String collectionName) {
        return collectionPrefix + "_" + collectionName;
    }
}
