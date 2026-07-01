package com.agent.mcp.dto;

import lombok.Data;

@Data
public class ToolInfoDTO {
    private String toolCode;
    private String toolName;
    private String description;
    private String toolType;
    private String source;
    private Object configSchema;
}
