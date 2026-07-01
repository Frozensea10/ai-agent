package com.agent.file.controller;

import com.agent.common.result.Result;
import com.agent.file.service.FileService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @PostMapping("/upload")
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
        String objectName = fileService.upload(file);
        return Result.success(objectName);
    }

    @GetMapping("/{objectName}")
    public void download(
            @PathVariable String objectName,
            @RequestParam(required = false, defaultValue = "inline") String disposition,
            HttpServletResponse response) {
        try {
            String contentType = fileService.detectContentType(objectName);
            response.setContentType(contentType);
            response.setHeader("Content-Disposition", disposition + "; filename=\"" + URLEncoder.encode(objectName, StandardCharsets.UTF_8) + "\"");

            try (InputStream is = fileService.download(objectName)) {
                byte[] buffer = new byte[4096];
                int len;
                while ((len = is.read(buffer)) != -1) {
                    response.getOutputStream().write(buffer, 0, len);
                }
                response.getOutputStream().flush();
            }
        } catch (Exception e) {
            log.error("文件下载异常", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/{objectName}/url")
    public Result<String> getPreviewUrl(@PathVariable String objectName) {
        String url = fileService.getPreviewUrl(objectName);
        return Result.success(url);
    }
}
