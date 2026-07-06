package com.agent.mcp.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class ToolExecuteRequest {
    @NotBlank(message = "toolCode 不能为空")
    private String toolCode;
    private Map<String, Object> parameters;
}
