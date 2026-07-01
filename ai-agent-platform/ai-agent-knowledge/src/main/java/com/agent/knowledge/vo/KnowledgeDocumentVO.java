package com.agent.knowledge.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KnowledgeDocumentVO {
    private Long id;
    private Long kbId;
    private String docName;
    private String docType;
    private String fileUrl;
    private Long fileSize;
    private Integer chunkCount;
    private Integer vectorStatus;
    private String errorMessage;
    private Integer status;
    private LocalDateTime createdAt;
}
