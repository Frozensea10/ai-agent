package com.agent.file.service;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.file.config.MinioConfig;
import com.agent.file.vo.FileDownloadVO;
import com.agent.file.vo.FileInfoVO;
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

import java.io.ByteArrayOutputStream;
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
public class FileServiceImpl implements FileService {

    /** MinIO 客户端 */
    private final MinioClient minioClient;

    /** MinIO 配置信息 */
    private final MinioConfig minioConfig;

    /** 最大允许上传的文件大小（50MB） */
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;

    /** 单次列出文件的最大数量 */
    private static final int MAX_LIST_LIMIT = 1000;

    /** MinIO 用户元数据 key：原始文件名 */
    private static final String META_ORIGINAL_FILENAME = "X-Amz-Meta-Original-Filename";
    /** MinIO 用户元数据 key：上传时间 */
    private static final String META_UPLOAD_TIME = "X-Amz-Meta-Upload-Time";
    /** 默认 MIME 类型（无法识别文件类型时使用） */
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
    /** 预签名 URL 有效期（天） */
    private static final int PREVIEW_URL_EXPIRY_DAYS = 7;

    /** 下载流读取缓冲区大小（字节） */
    private static final int BUFFER_SIZE = 4096;

    /** 扩展名（小写、含点号）到 MIME 类型的映射表 */
    private static final Map<String, String> EXTENSION_MIME_MAP = Map.ofEntries(
            Map.entry(".pdf", "application/pdf"),
            Map.entry(".txt", "text/plain"),
            Map.entry(".md", "text/markdown"),
            Map.entry(".json", "application/json"),
            Map.entry(".png", "image/png"),
            Map.entry(".jpg", "image/jpeg"),
            Map.entry(".jpeg", "image/jpeg"),
            Map.entry(".gif", "image/gif"),
            Map.entry(".svg", "image/svg+xml"),
            Map.entry(".mp4", "video/mp4"),
            Map.entry(".doc", "application/msword"),
            Map.entry(".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry(".xls", "application/vnd.ms-excel"),
            Map.entry(".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry(".ppt", "application/vnd.ms-powerpoint"),
            Map.entry(".pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation")
    );

    @Override
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
        userMetadata.put(META_ORIGINAL_FILENAME, originalFilename != null ? originalFilename : objectName);
        userMetadata.put(META_UPLOAD_TIME, LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));

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

    @Override
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

    @Override
    public String getPreviewUrl(String objectName) {
        ensureBucketExists();
        try {
            return minioClient.getPresignedObjectUrl(
                    io.minio.GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(minioConfig.getBucketName())
                            .object(objectName)
                            .expiry(PREVIEW_URL_EXPIRY_DAYS, TimeUnit.DAYS)
                            .build()
            );
        } catch (Exception e) {
            log.error("生成文件访问链接失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR.getCode(), "生成文件访问链接失败");
        }
    }

    @Override
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

    @Override
    public List<FileInfoVO> listFileInfos() {
        List<String> objectNames = listObjects();
        List<FileInfoVO> list = new ArrayList<>();
        for (String objectName : objectNames) {
            try {
                Map<String, Object> info = getObjectInfo(objectName);
                FileInfoVO vo = new FileInfoVO();
                vo.setObjectName(info.get("objectName") instanceof String ? (String) info.get("objectName") : objectName);
                vo.setOriginalName(info.get("originalName") instanceof String ? (String) info.get("originalName") : objectName);
                vo.setUploadTime(info.get("uploadTime") instanceof String ? (String) info.get("uploadTime") : null);
                vo.setSize(info.get("size") instanceof Number ? ((Number) info.get("size")).longValue() : null);
                vo.setContentType(info.get("contentType") instanceof String ? (String) info.get("contentType") : null);
                vo.setLastModified(info.get("lastModified"));
                vo.setUrl(getPreviewUrl(objectName));
                list.add(vo);
            } catch (Exception e) {
                log.warn("获取文件信息失败, objectName={}", objectName, e);
            }
        }
        return list;
    }

    @Override
    public FileDownloadVO downloadFile(String objectName) {
        if (objectName == null || objectName.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "对象名称不能为空");
        }
        Map<String, Object> info = getObjectInfo(objectName);
        Long size = info.get("size") instanceof Number ? ((Number) info.get("size")).longValue() : null;
        String contentType = info.get("contentType") instanceof String ? (String) info.get("contentType") : detectContentType(objectName);

        try (InputStream is = download(objectName);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int len;
            while ((len = is.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            return new FileDownloadVO(out.toByteArray(), objectName, contentType, size);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("文件下载失败, objectName={}", objectName, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR.getCode(), "文件下载失败: " + e.getMessage());
        }
    }

    @Override
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

    @Override
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
            info.put("originalName", getMetadataValue(stat.userMetadata(), META_ORIGINAL_FILENAME, stat.object()));
            info.put("uploadTime", getMetadataValue(stat.userMetadata(), META_UPLOAD_TIME, stat.lastModified() != null ? stat.lastModified().toString() : null));
            info.put("size", stat.size());
            info.put("contentType", stat.contentType());
            info.put("lastModified", stat.lastModified());
            return info;
        } catch (Exception e) {
            log.error("获取文件信息失败, objectName={}", objectName, e);
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "文件不存在或获取信息失败");
        }
    }

    @Override
    public String detectContentType(String objectName) {
        if (objectName == null) {
            return DEFAULT_CONTENT_TYPE;
        }
        String lower = objectName.toLowerCase();
        int dotIndex = lower.lastIndexOf('.');
        if (dotIndex < 0) {
            return DEFAULT_CONTENT_TYPE;
        }
        return EXTENSION_MIME_MAP.getOrDefault(lower.substring(dotIndex), DEFAULT_CONTENT_TYPE);
    }

    /**
     * 从用户元数据中读取指定 key 的值，兼容大小写。
     *
     * @param userMetadata 用户元数据 Map
     * @param key          目标 key
     * @param defaultValue 默认值
     * @return 读取到的值，不存在时返回默认值
     */
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

    /**
     * 确保配置的存储桶存在，不存在则自动创建。
     *
     * @throws BusinessException 当检查或创建存储桶失败时抛出
     */
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
