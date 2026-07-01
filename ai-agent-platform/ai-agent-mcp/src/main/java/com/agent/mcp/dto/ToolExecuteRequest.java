package com.agent.mcp.dto;

import lombok.Data;

import java.util.Map;

@Data
public class ToolExecuteRequest {
    private String toolCode;
    private Map<String, Object> parameters;
}
