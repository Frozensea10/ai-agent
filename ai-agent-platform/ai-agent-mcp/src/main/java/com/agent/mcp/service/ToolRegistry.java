package com.agent.mcp.service;

import com.agent.mcp.dto.ToolInfoDTO;
import com.agent.mcp.entity.McpServer;
import com.agent.mcp.tool.BuiltInToolExecutor;
import lombok.Data;

import java.util.List;
import java.util.Map;

public interface ToolRegistry {

    BuiltInToolExecutor getBuiltInTool(String toolCode);

    McpToolInfo getMcpTool(String toolCode);

    boolean hasTool(String toolCode);

    boolean isBuiltInTool(String toolCode);

    boolean isMcpTool(String toolCode);

    List<ToolInfoDTO> listAllTools();

    List<ToolInfoDTO> listMcpTools();

    void refreshMcpServers();

    void addMcpServer(McpServer server);

    void removeMcpServer(String serverName);

    Map<String, String> getServerConnectionStatus();

    @Data
    class McpToolInfo {
        private String toolName;
        private String serverName;
        private String description;
        private Object inputSchema;
    }
}
