package com.agent.file.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 文件下载结果视图对象，封装文件字节流及响应所需的元信息。
 */
@Data
@AllArgsConstructor
public class FileDownloadVO {
    /** 文件字节内容 */
    private byte[] data;
    /** 文件对象名称 */
    private String fileName;
    /** Content-Type */
    private String contentType;
    /** 文件大小（字节），未知时为 {@code null} */
    private Long size;
}
