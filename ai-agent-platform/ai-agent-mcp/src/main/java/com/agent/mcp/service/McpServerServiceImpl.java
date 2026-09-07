package com.agent.mcp.service;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.mcp.dto.McpServerDTO;
import com.agent.mcp.entity.McpServer;
import com.agent.mcp.mapper.McpServerMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

import static com.agent.mcp.service.ToolRegistryConstants.SERVER_STATUS_ACTIVE;

@Slf4j
@Service
@RequiredArgsConstructor
public class McpServerServiceImpl implements McpServerService {

    private final McpServerMapper mcpServerMapper;
    private final ToolRegistry toolRegistry;

    @Override
    public List<McpServerDTO> listServers() {
        List<McpServer> servers = mcpServerMapper.selectList(
                new LambdaQueryWrapper<McpServer>().orderByDesc(McpServer::getCreatedAt)
        );
        return servers.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Override
    public McpServerDTO getServer(Long id) {
        McpServer server = mcpServerMapper.selectById(id);
        if (server == null) {
            return null;
        }
        return convertToDTO(server);
    }

    @Override
    @Transactional
    public McpServerDTO createServer(McpServerDTO dto) {
        McpServer server = new McpServer();
        server.setServerName(dto.getServerName());
        server.setServerType(dto.getServerType());
        server.setTransportConfig(dto.getTransportConfig());
        server.setStatus(SERVER_STATUS_ACTIVE);

        mcpServerMapper.insert(server);

        // 建立真实的 MCP 连接
        try {
            toolRegistry.addMcpServer(server);
        } catch (Exception e) {
            log.error("MCP Server 连接失败，但配置已保存: {}", server.getServerName(), e);
        }

        return convertToDTO(server);
    }

    @Override
    @Transactional
    public boolean updateServer(Long id, McpServerDTO dto) {
        McpServer server = mcpServerMapper.selectById(id);
        if (server == null) {
            return false;
        }

        // 先断开旧连接
        toolRegistry.removeMcpServer(server.getServerName());

        server.setServerName(dto.getServerName());
        server.setServerType(dto.getServerType());
        server.setTransportConfig(dto.getTransportConfig());
        mcpServerMapper.updateById(server);

        // 重新建立连接
        if (SERVER_STATUS_ACTIVE.equals(server.getStatus())) {
            try {
                toolRegistry.addMcpServer(server);
            } catch (Exception e) {
                log.error("MCP Server 更新后连接失败: {}", server.getServerName(), e);
            }
        }

        return true;
    }

    @Override
    @Transactional
    public boolean deleteServer(Long id) {
        McpServer server = mcpServerMapper.selectById(id);
        if (server == null) {
            return false;
        }
        mcpServerMapper.deleteById(id);
        toolRegistry.removeMcpServer(server.getServerName());
        return true;
    }

    @Override
    public boolean updateServerStatus(Long id, String status) {
        McpServer server = mcpServerMapper.selectById(id);
        if (server == null) {
            return false;
        }
        server.setStatus(status);
        mcpServerMapper.updateById(server);

        if (SERVER_STATUS_ACTIVE.equals(status)) {
            toolRegistry.addMcpServer(server);
        } else {
            toolRegistry.removeMcpServer(server.getServerName());
        }
        return true;
    }

    private McpServerDTO convertToDTO(McpServer server) {
        McpServerDTO dto = new McpServerDTO();
        dto.setId(server.getId());
        dto.setServerName(server.getServerName());
        dto.setServerType(server.getServerType());
        dto.setTransportConfig(server.getTransportConfig());
        dto.setStatus(server.getStatus());
        return dto;
    }
}
