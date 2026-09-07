package com.agent.file.service;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.file.config.MinioConfig;
import com.agent.file.vo.FileDownloadVO;
import com.agent.file.vo.FileInfoVO;
import io.minio.*;
import io.minio.messages.Item;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.*;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @Mock
    private MinioClient minioClient;

    @Mock
    private MinioConfig minioConfig;

    @InjectMocks
    private FileServiceImpl fileService;

    private static final String BUCKET_NAME = "ai-agent-bucket";

    @BeforeEach
    void setUp() {
        given(minioConfig.getBucketName()).willReturn(BUCKET_NAME);
    }

    private void stubBucketExists() throws Exception {
        given(minioClient.bucketExists(any(BucketExistsArgs.class))).willReturn(true);
    }

    @Test
    void upload_shouldThrow_whenFileIsEmpty() {
        MultipartFile emptyFile = new MockMultipartFile("file", "test.txt", "text/plain", new byte[0]);

        assertThatThrownBy(() -> fileService.upload(emptyFile))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode());
                    assertThat(be.getMessage()).isEqualTo("文件不能为空");
                });
    }

    @Test
    void upload_shouldThrow_whenFileExceedsSizeLimit() {
        byte[] oversized = new byte[(int) (50 * 1024 * 1024) + 1];
        MultipartFile largeFile = new MockMultipartFile("file", "big.bin", "application/octet-stream", oversized);

        assertThatThrownBy(() -> fileService.upload(largeFile))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode());
                    assertThat(be.getMessage()).isEqualTo("文件大小不能超过 50MB");
                });
    }

    @Test
    void upload_shouldSuccess_whenFileIsValid() throws Exception {
        stubBucketExists();
        byte[] content = "Hello World".getBytes();
        MultipartFile file = new MockMultipartFile("file", "hello.txt", "text/plain", content);

        String objectName = fileService.upload(file);

        assertThat(objectName).isNotBlank().endsWith(".txt");
        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    void upload_shouldWrapException_whenMinioPutFails() throws Exception {
        stubBucketExists();
        willThrow(new RuntimeException("MinIO down")).given(minioClient).putObject(any(PutObjectArgs.class));
        byte[] content = "Hello World".getBytes();
        MultipartFile file = new MockMultipartFile("file", "hello.txt", "text/plain", content);

        assertThatThrownBy(() -> fileService.upload(file))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getCode()).isEqualTo(ErrorCode.SYSTEM_ERROR.getCode());
                    assertThat(be.getMessage()).contains("文件上传失败");
                });
    }

    @Test
    void download_shouldReturnInputStream_whenObjectExists() throws Exception {
        stubBucketExists();
        GetObjectResponse fakeResponse = mock(GetObjectResponse.class);
        given(minioClient.getObject(any(GetObjectArgs.class))).willReturn(fakeResponse);

        InputStream result = fileService.download("object.txt");

        assertThat(result).isNotNull();
        verify(minioClient).getObject(any(GetObjectArgs.class));
    }

    @Test
    void download_shouldThrowNotFound_whenMinioFails() throws Exception {
        stubBucketExists();
        given(minioClient.getObject(any(GetObjectArgs.class))).willThrow(new RuntimeException("not found"));

        assertThatThrownBy(() -> fileService.download("missing.txt"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getCode()).isEqualTo(ErrorCode.NOT_FOUND.getCode());
                    assertThat(be.getMessage()).isEqualTo("文件不存在或下载失败");
                });
    }

    @Test
    void getPreviewUrl_shouldReturnPresignedUrl() throws Exception {
        stubBucketExists();
        given(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class))).willReturn("http://minio/presigned");

        String url = fileService.getPreviewUrl("object.txt");

        assertThat(url).isEqualTo("http://minio/presigned");
    }

    @Test
    void getPreviewUrl_shouldThrowSystemError_whenMinioFails() throws Exception {
        stubBucketExists();
        given(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class))).willThrow(new RuntimeException("fail"));

        assertThatThrownBy(() -> fileService.getPreviewUrl("object.txt"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getCode()).isEqualTo(ErrorCode.SYSTEM_ERROR.getCode());
                });
    }

    @Test
    void listObjects_shouldReturnObjectNames() throws Exception {
        stubBucketExists();
        Item item = mock(Item.class);
        given(item.objectName()).willReturn("a.txt");
        Result<Item> result = new Result<>(item);
        given(minioClient.listObjects(any(ListObjectsArgs.class))).willReturn(List.of(result));

        List<String> objectNames = fileService.listObjects();

        assertThat(objectNames).containsExactly("a.txt");
    }

    @Test
    void deleteObject_shouldThrow_whenObjectNameIsBlank() {
        assertThatThrownBy(() -> fileService.deleteObject("  "))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode());
                    assertThat(be.getMessage()).isEqualTo("对象名称不能为空");
                });
    }

    @Test
    void deleteObject_shouldSuccess_whenObjectNameValid() throws Exception {
        stubBucketExists();
        String objectName = UUID.randomUUID().toString();

        fileService.deleteObject(objectName);

        verify(minioClient).removeObject(any(RemoveObjectArgs.class));
    }

    @Test
    void getObjectInfo_shouldReturnMap_whenObjectExists() throws Exception {
        stubBucketExists();
        StatObjectResponse stat = mock(StatObjectResponse.class);
        given(stat.object()).willReturn("object.txt");
        given(stat.size()).willReturn(1024L);
        given(stat.contentType()).willReturn("text/plain");
        given(stat.lastModified()).willReturn(ZonedDateTime.now());
        given(minioClient.statObject(any(StatObjectArgs.class))).willReturn(stat);

        Map<String, Object> info = fileService.getObjectInfo("object.txt");

        assertThat(info).containsEntry("objectName", "object.txt")
                .containsEntry("size", 1024L)
                .containsEntry("contentType", "text/plain")
                .containsKey("lastModified");
    }

    @Test
    void getObjectInfo_shouldThrowNotFound_whenMinioFails() throws Exception {
        stubBucketExists();
        given(minioClient.statObject(any(StatObjectArgs.class))).willThrow(new RuntimeException("not found"));

        assertThatThrownBy(() -> fileService.getObjectInfo("missing.txt"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getCode()).isEqualTo(ErrorCode.NOT_FOUND.getCode());
                });
    }

    @ParameterizedTest
    @CsvSource({
            ".pdf, application/pdf",
            ".txt, text/plain",
            ".md, text/markdown",
            ".json, application/json",
            ".png, image/png",
            ".jpg, image/jpeg",
            ".jpeg, image/jpeg",
            ".gif, image/gif",
            ".svg, image/svg+xml",
            ".mp4, video/mp4",
            ".doc, application/msword",
            ".docx, application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            ".xls, application/vnd.ms-excel",
            ".xlsx, application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            ".ppt, application/vnd.ms-powerpoint",
            ".pptx, application/vnd.openxmlformats-officedocument.presentationml.presentation",
            ".unknown, application/octet-stream"
    })
    void detectContentType_shouldReturnExpectedType(String extension, String expectedType) {
        String objectName = "file" + extension;
        assertThat(fileService.detectContentType(objectName)).isEqualTo(expectedType);
    }

    @Test
    void detectContentType_shouldReturnDefaultType_whenObjectNameIsNull() {
        assertThat(fileService.detectContentType(null)).isEqualTo("application/octet-stream");
    }

    @Test
    void listFileInfos_shouldReturnVOListWithUrl() throws Exception {
        stubBucketExists();
        Item item = mock(Item.class);
        given(item.objectName()).willReturn("a.txt");
        Result<Item> result = new Result<>(item);
        given(minioClient.listObjects(any(ListObjectsArgs.class))).willReturn(List.of(result));

        StatObjectResponse stat = mock(StatObjectResponse.class);
        given(stat.object()).willReturn("a.txt");
        given(stat.size()).willReturn(100L);
        given(stat.contentType()).willReturn("text/plain");
        given(stat.lastModified()).willReturn(ZonedDateTime.now());
        given(minioClient.statObject(any(StatObjectArgs.class))).willReturn(stat);
        given(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class))).willReturn("http://minio/a.txt");

        List<FileInfoVO> list = fileService.listFileInfos();

        assertThat(list).hasSize(1);
        FileInfoVO vo = list.get(0);
        assertThat(vo.getObjectName()).isEqualTo("a.txt");
        assertThat(vo.getSize()).isEqualTo(100L);
        assertThat(vo.getContentType()).isEqualTo("text/plain");
        assertThat(vo.getUrl()).isEqualTo("http://minio/a.txt");
    }

    @Test
    void listFileInfos_shouldSkipObject_whenInfoQueryFails() throws Exception {
        stubBucketExists();
        Item item = mock(Item.class);
        given(item.objectName()).willReturn("broken.txt");
        Result<Item> result = new Result<>(item);
        given(minioClient.listObjects(any(ListObjectsArgs.class))).willReturn(List.of(result));
        given(minioClient.statObject(any(StatObjectArgs.class))).willThrow(new RuntimeException("stat fail"));

        List<FileInfoVO> list = fileService.listFileInfos();

        assertThat(list).isEmpty();
    }

    @Test
    void downloadFile_shouldReturnVO_whenObjectExists() throws Exception {
        stubBucketExists();
        StatObjectResponse stat = mock(StatObjectResponse.class);
        given(stat.object()).willReturn("object.txt");
        given(stat.size()).willReturn(5L);
        given(stat.contentType()).willReturn("text/plain");
        given(stat.lastModified()).willReturn(ZonedDateTime.now());
        given(minioClient.statObject(any(StatObjectArgs.class))).willReturn(stat);

        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);
        GetObjectResponse fakeResponse = new GetObjectResponse(
                null, BUCKET_NAME, "default", "object.txt", new ByteArrayInputStream(content));
        given(minioClient.getObject(any(GetObjectArgs.class))).willReturn(fakeResponse);

        FileDownloadVO vo = fileService.downloadFile("object.txt");

        assertThat(vo.getFileName()).isEqualTo("object.txt");
        assertThat(vo.getContentType()).isEqualTo("text/plain");
        assertThat(vo.getSize()).isEqualTo(5L);
        assertThat(vo.getData()).isEqualTo(content);
    }

    @Test
    void downloadFile_shouldThrowParamError_whenObjectNameBlank() {
        assertThatThrownBy(() -> fileService.downloadFile("  "))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode());
                });
    }

    @Test
    void downloadFile_shouldThrowNotFound_whenObjectMissing() throws Exception {
        stubBucketExists();
        given(minioClient.statObject(any(StatObjectArgs.class))).willThrow(new RuntimeException("not found"));

        assertThatThrownBy(() -> fileService.downloadFile("missing.txt"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getCode()).isEqualTo(ErrorCode.NOT_FOUND.getCode());
                });
    }
}
