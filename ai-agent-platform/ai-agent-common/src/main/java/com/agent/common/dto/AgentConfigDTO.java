package com.agent.common.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AgentConfigDTO {

    private Long id;
    private String agentName;
    private String agentCode;
    private String description;
    private String modelProvider;
    private String modelName;
    private String systemPrompt;
    private Double temperature;
    private Integer maxTokens;
    private String memoryType;
    private Integer memoryMaxMessages;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
