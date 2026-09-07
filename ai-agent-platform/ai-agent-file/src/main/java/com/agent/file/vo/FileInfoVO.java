package com.agent.file.vo;

import lombok.Data;

/**
 * 文件信息视图对象，用于向前端返回文件元数据及预览地址。
 */
@Data
public class FileInfoVO {
    /** 文件对象名称 */
    private String objectName;
    /** 原始文件名 */
    private String originalName;
    /** 上传时间 */
    private String uploadTime;
    /** 文件大小（字节） */
    private Long size;
    /** Content-Type */
    private String contentType;
    /** 最后修改时间 */
    private Object lastModified;
    /** 文件预览地址 */
    private String url;
}
