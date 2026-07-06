package com.agent.mcp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class McpServerDTO {
    private Long id;

    @NotBlank(message = "服务器名称不能为空")
    private String serverName;

    @NotBlank(message = "服务器类型不能为空")
    @Pattern(regexp = "http|sse|stdio", message = "服务器类型必须为 http、sse 或 stdio")
    private String serverType;

    @NotBlank(message = "传输配置不能为空")
    private String transportConfig;

    private String status;
}
