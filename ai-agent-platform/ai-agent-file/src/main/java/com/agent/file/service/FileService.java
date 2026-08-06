package com.agent.file.service;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.file.config.MinioConfig;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.http.Method;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024;
    private static final int MAX_LIST_LIMIT = 1000;

    public String upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "文件大小不能超过 50MB");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String objectName = UUID.randomUUID().toString().replace("-", "") + extension;

        Map<String, String> userMetadata = new HashMap<>();
        userMetadata.put("X-Amz-Meta-Original-Filename", originalFilename != null ? originalFilename : objectName);
        userMetadata.put("X-Amz-Meta-Upload-Time", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));

        ensureBucketExists();

        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .object(objectName)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .userMetadata(userMetadata)
                            .build()
            );
            return objectName;
        } catch (Exception e) {
            log.error("文件上传失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR.getCode(), "文件上传失败: " + e.getMessage());
        }
    }

    public InputStream download(String objectName) {
        ensureBucketExists();
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .object(objectName)
                            .build()
            );
        } catch (Exception e) {
            log.error("文件下载失败, objectName={}", objectName, e);
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "文件不存在或下载失败");
        }
    }

    public String getPreviewUrl(String objectName) {
        ensureBucketExists();
        try {
            return minioClient.getPresignedObjectUrl(
                    io.minio.GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(minioConfig.getBucketName())
                            .object(objectName)
                            .expiry(7, TimeUnit.DAYS)
                            .build()
            );
        } catch (Exception e) {
            log.error("生成文件访问链接失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR.getCode(), "生成文件访问链接失败");
        }
    }

    public List<String> listObjects() {
        ensureBucketExists();
        List<String> objectNames = new ArrayList<>();
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .maxKeys(MAX_LIST_LIMIT)
                            .build()
            );
            int count = 0;
            for (Result<Item> result : results) {
                if (count >= MAX_LIST_LIMIT) {
                    break;
                }
                Item item = result.get();
                objectNames.add(item.objectName());
                count++;
            }
            return objectNames;
        } catch (Exception e) {
            log.error("文件列表查询失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR.getCode(), "文件列表查询失败: " + e.getMessage());
        }
    }

    public void deleteObject(String objectName) {
        if (objectName == null || objectName.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "对象名称不能为空");
        }
        ensureBucketExists();
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .object(objectName)
                            .build()
            );
        } catch (Exception e) {
            log.error("文件删除失败, objectName={}", objectName, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR.getCode(), "文件删除失败: " + e.getMessage());
        }
    }

    public Map<String, Object> getObjectInfo(String objectName) {
        if (objectName == null || objectName.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "对象名称不能为空");
        }
        ensureBucketExists();
        try {
            StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .object(objectName)
                            .build()
            );
            Map<String, Object> info = new HashMap<>();
            info.put("objectName", stat.object());
            info.put("originalName", getMetadataValue(stat.userMetadata(), "X-Amz-Meta-Original-Filename", stat.object()));
            info.put("uploadTime", getMetadataValue(stat.userMetadata(), "X-Amz-Meta-Upload-Time", stat.lastModified() != null ? stat.lastModified().toString() : null));
            info.put("size", stat.size());
            info.put("contentType", stat.contentType());
            info.put("lastModified", stat.lastModified());
            return info;
        } catch (Exception e) {
            log.error("获取文件信息失败, objectName={}", objectName, e);
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "文件不存在或获取信息失败");
        }
    }

    public String detectContentType(String objectName) {
        if (objectName == null) {
            return "application/octet-stream";
        }
        String lower = objectName.toLowerCase();
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".txt")) return "text/plain";
        if (lower.endsWith(".md")) return "text/markdown";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".doc")) return "application/msword";
        if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (lower.endsWith(".xls")) return "application/vnd.ms-excel";
        if (lower.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (lower.endsWith(".ppt")) return "application/vnd.ms-powerpoint";
        if (lower.endsWith(".pptx")) return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        return "application/octet-stream";
    }

    private String getMetadataValue(Map<String, String> userMetadata, String key, String defaultValue) {
        if (userMetadata == null) {
            return defaultValue;
        }
        String value = userMetadata.get(key);
        if (value == null || value.isBlank()) {
            value = userMetadata.get(key.toLowerCase());
        }
        return value != null && !value.isBlank() ? value : defaultValue;
    }

    private void ensureBucketExists() {
        try {
            boolean exists = minioClient.bucketExists(
                    io.minio.BucketExistsArgs.builder().bucket(minioConfig.getBucketName()).build()
            );
            if (!exists) {
                minioClient.makeBucket(
                        io.minio.MakeBucketArgs.builder().bucket(minioConfig.getBucketName()).build()
                );
            }
        } catch (Exception e) {
            log.error("MinIO bucket 检查失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR.getCode(), "MinIO bucket 检查失败");
        }
    }
}
