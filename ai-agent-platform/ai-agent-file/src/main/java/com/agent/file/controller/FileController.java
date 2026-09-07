package com.agent.file.controller;

import com.agent.common.result.Result;
import com.agent.file.service.FileService;
import com.agent.file.vo.FileDownloadVO;
import com.agent.file.vo.FileInfoVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 文件管理控制器，提供文件上传、下载、删除、列表及预览地址等 REST 接口。
 *
 * @author agent
 */
@Tag(name = "文件管理", description = "文件上传、下载、列表与预览接口")
@Slf4j
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    /** 文件业务服务 */
    private final FileService fileService;

    /** Content-Disposition 取值：内联预览 */
    private static final String DISPOSITION_INLINE = "inline";
    /** Content-Disposition 文件名参数格式 */
    private static final String DISPOSITION_FILENAME_FORMAT = "; filename=\"%s\"";

    /**
     * 上传文件。
     *
     * @param file 待上传的文件
     * @return 文件对象名称
     */
    @Operation(summary = "上传文件", description = "上传单个文件到对象存储")
    @PostMapping("/upload")
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
        return Result.success(fileService.upload(file));
    }

    /**
     * 查询文件列表。
     *
     * @return 包含文件元数据及预览地址的列表
     */
    @Operation(summary = "查询文件列表", description = "查询对象存储中所有文件的基本信息")
    @GetMapping
    public Result<List<FileInfoVO>> listFiles() {
        return Result.success(fileService.listFileInfos());
    }

    /**
     * 下载文件，支持内联预览或附件下载。
     *
     * @param objectName 文件对象名称
     * @param disposition 下载方式（inline / attachment），默认 inline
     * @param response    HTTP 响应对象，用于输出文件流
     * @throws IOException 响应写入失败时抛出
     */
    @GetMapping("/{objectName}")
    public void download(
            @PathVariable String objectName,
            @RequestParam(required = false, defaultValue = DISPOSITION_INLINE) String disposition,
            HttpServletResponse response) throws IOException {
        FileDownloadVO file = fileService.downloadFile(objectName);

        response.setContentType(file.getContentType());
        if (file.getSize() != null && file.getSize() > 0) {
            response.setContentLengthLong(file.getSize());
        }
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, disposition + String.format(DISPOSITION_FILENAME_FORMAT, URLEncoder.encode(file.getFileName(), StandardCharsets.UTF_8)));
        response.getOutputStream().write(file.getData());
        response.getOutputStream().flush();
    }

    /**
     * 删除指定文件。
     *
     * @param objectName 文件对象名称
     * @return 空结果
     */
    @DeleteMapping("/{objectName}")
    public Result<Void> delete(@PathVariable String objectName) {
        fileService.deleteObject(objectName);
        return Result.success();
    }

    /**
     * 获取文件预览地址。
     *
     * @param objectName 文件对象名称
     * @return 可直接访问的文件 URL
     */
    @Operation(summary = "获取文件预览地址", description = "根据对象名称获取文件访问 URL")
    @GetMapping("/{objectName}/url")
    public Result<String> getPreviewUrl(@PathVariable String objectName) {
        return Result.success(fileService.getPreviewUrl(objectName));
    }
}
