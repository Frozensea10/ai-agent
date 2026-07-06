package com.agent.knowledge.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KnowledgeBaseVO {
    private Long id;
    private String kbName;
    private String kbCode;
    private String description;
    private String embeddingModel;
    private String embeddingProvider;
    private Integer documentCount;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
