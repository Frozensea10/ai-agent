package com.agent.mcp.service;

import com.agent.mcp.dto.McpServerDTO;

import java.util.List;

public interface McpServerService {

    List<McpServerDTO> listServers();

    McpServerDTO getServer(Long id);

    McpServerDTO createServer(McpServerDTO dto);

    boolean updateServer(Long id, McpServerDTO dto);

    boolean deleteServer(Long id);

    boolean updateServerStatus(Long id, String status);
}
