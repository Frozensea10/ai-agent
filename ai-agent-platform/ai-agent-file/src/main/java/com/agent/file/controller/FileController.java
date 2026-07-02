package com.agent.file.controller;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.common.result.Result;
import com.agent.file.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "文件管理", description = "文件上传、下载、列表与预览接口")
@Slf4j
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @Operation(summary = "上传文件", description = "上传单个文件到对象存储")
    @PostMapping("/upload")
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "上传文件不能为空");
        }
        String objectName = fileService.upload(file);
        return Result.success(objectName);
    }

    @Operation(summary = "查询文件列表", description = "查询对象存储中所有文件的基本信息")
    @GetMapping
    public Result<List<Map<String, Object>>> listFiles() {
        List<String> objectNames = fileService.listObjects();
        List<Map<String, Object>> list = new ArrayList<>();
        for (String objectName : objectNames) {
            try {
                Map<String, Object> info = fileService.getObjectInfo(objectName);
                info.put("url", fileService.getPreviewUrl(objectName));
                list.add(info);
            } catch (Exception e) {
                log.warn("获取文件信息失败, objectName={}", objectName, e);
            }
        }
        return Result.success(list);
    }

    @GetMapping("/{objectName}")
    public void download(
            @PathVariable String objectName,
            @RequestParam(required = false, defaultValue = "inline") String disposition,
            HttpServletResponse response) {
        if (objectName == null || objectName.isBlank()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        try {
            Map<String, Object> info = fileService.getObjectInfo(objectName);
            Long size = info.get("size") instanceof Number ? ((Number) info.get("size")).longValue() : null;
            String contentType = info.get("contentType") instanceof String ? (String) info.get("contentType") : fileService.detectContentType(objectName);

            response.setContentType(contentType);
            if (size != null && size > 0) {
                response.setContentLengthLong(size);
            }
            response.setHeader("Content-Disposition", disposition + "; filename=\"" + URLEncoder.encode(objectName, StandardCharsets.UTF_8) + "\"");

            try (InputStream is = fileService.download(objectName)) {
                byte[] buffer = new byte[4096];
                int len;
                while ((len = is.read(buffer)) != -1) {
                    response.getOutputStream().write(buffer, 0, len);
                }
                response.getOutputStream().flush();
            }
        } catch (BusinessException e) {
            if (e.getCode() == ErrorCode.NOT_FOUND.getCode()) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            } else {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        } catch (Exception e) {
            log.error("文件下载异常, objectName={}", objectName, e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @DeleteMapping("/{objectName}")
    public Result<Void> delete(@PathVariable String objectName) {
        if (objectName == null || objectName.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "对象名称不能为空");
        }
        fileService.deleteObject(objectName);
        return Result.success();
    }

    @Operation(summary = "获取文件预览地址", description = "根据对象名称获取文件访问 URL")
    @GetMapping("/{objectName}/url")
    public Result<String> getPreviewUrl(@PathVariable String objectName) {
        String url = fileService.getPreviewUrl(objectName);
        return Result.success(url);
    }
}
