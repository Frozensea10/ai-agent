package com.agent.mcp.dto;

import lombok.Data;

@Data
public class ToolExecuteResult {
    private boolean success;
    private String toolCode;
    private Object data;
    private String errorMessage;
    private Long executeTimeMs;
}
