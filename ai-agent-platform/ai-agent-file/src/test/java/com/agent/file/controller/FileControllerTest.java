package com.agent.file.controller;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.common.exception.GlobalExceptionHandler;
import com.agent.file.config.MinioConfig;
import com.agent.file.service.FileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(GlobalExceptionHandler.class)
@WebMvcTest(FileController.class)
class FileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FileService fileService;

    @MockBean
    private MinioConfig minioConfig;

    @Test
    void upload_shouldReturnObjectName_whenFileValid() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "hello.txt", "text/plain", "content".getBytes());
        given(fileService.upload(any())).willReturn("abc123.txt");

        mockMvc.perform(multipart("/api/v1/files/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data", is("abc123.txt")));
    }

    @Test
    void upload_shouldReturnBadRequest_whenFileEmpty() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);
        doThrow(new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "上传文件不能为空"))
                .when(fileService).upload(any());

        mockMvc.perform(multipart("/api/v1/files/upload").file(emptyFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(400)))
                .andExpect(jsonPath("$.message", is("上传文件不能为空")));
    }

    @Test
    void listFiles_shouldReturnFileList() throws Exception {
        given(fileService.listObjects()).willReturn(List.of("a.txt", "b.txt"));

        Map<String, Object> infoA = new HashMap<>();
        infoA.put("objectName", "a.txt");
        infoA.put("size", 100L);
        infoA.put("contentType", "text/plain");
        infoA.put("url", "http://minio/a.txt");

        given(fileService.getObjectInfo(anyString())).willReturn(infoA);
        given(fileService.getPreviewUrl(anyString())).willReturn("http://minio/a.txt");

        mockMvc.perform(get("/api/v1/files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data[0].objectName", is("a.txt")));
    }

    @Test
    void delete_shouldReturnSuccess_whenObjectNameValid() throws Exception {
        doNothing().when(fileService).deleteObject(anyString());

        mockMvc.perform(delete("/api/v1/files/{objectName}", "abc123.txt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)));
    }

    @Test
    void delete_shouldReturnBadRequest_whenObjectNameBlank() throws Exception {
        doThrow(new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "对象名称不能为空"))
                .when(fileService).deleteObject(anyString());

        mockMvc.perform(delete("/api/v1/files/{objectName}", "missing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(400)))
                .andExpect(jsonPath("$.message", is("对象名称不能为空")));
    }

    @Test
    void getPreviewUrl_shouldReturnUrl() throws Exception {
        given(fileService.getPreviewUrl(anyString())).willReturn("http://minio/presigned");

        mockMvc.perform(get("/api/v1/files/{objectName}/url", "abc123.txt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data", is("http://minio/presigned")));
    }
}
