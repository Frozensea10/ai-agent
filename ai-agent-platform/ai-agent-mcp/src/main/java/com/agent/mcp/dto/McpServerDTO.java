package com.agent.mcp.dto;

import lombok.Data;

@Data
public class McpServerDTO {
    private Long id;
    private String serverName;
    private String serverType;
    private String transportConfig;
    private String status;
}
