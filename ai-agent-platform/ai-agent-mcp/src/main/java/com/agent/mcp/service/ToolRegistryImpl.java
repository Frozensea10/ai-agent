package com.agent.mcp.service;

import com.agent.mcp.client.McpClientManager;
import com.agent.mcp.dto.ToolInfoDTO;
import com.agent.mcp.entity.BuiltInTool;
import com.agent.mcp.entity.McpServer;
import com.agent.mcp.mapper.BuiltInToolMapper;
import com.agent.mcp.mapper.McpServerMapper;
import com.agent.mcp.tool.BuiltInToolExecutor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.agent.mcp.client.McpClientConstants.*;
import static com.agent.mcp.service.ToolRegistryConstants.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ToolRegistryImpl implements ToolRegistry {

    private final List<BuiltInToolExecutor> builtInToolExecutors;
    private final BuiltInToolMapper builtInToolMapper;
    private final McpServerMapper mcpServerMapper;
    private final McpClientManager mcpClientManager;
    private final ObjectMapper objectMapper;

    private final Map<String, BuiltInToolExecutor> builtInToolMap = new ConcurrentHashMap<>();
    private final Map<String, McpServer> mcpServerMap = new ConcurrentHashMap<>();
    private final Map<String, McpToolInfo> mcpToolMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("初始化工具注册表...");
        registerBuiltInTools();
        // 异步加载 MCP Server，避免阻塞主线程
        CompletableFuture.runAsync(this::loadMcpServers);
        log.info("工具注册表初始化完成，内置工具: {}", builtInToolMap.size());
    }

    private void registerBuiltInTools() {
        for (BuiltInToolExecutor executor : builtInToolExecutors) {
            String toolCode = executor.getToolCode();
            builtInToolMap.put(toolCode, executor);

            BuiltInTool tool = new BuiltInTool();
            tool.setToolName(executor.getToolName());
            tool.setToolCode(toolCode);
            tool.setDescription(executor.getDescription());
            tool.setToolType(TOOL_TYPE_BUILT_IN);
            tool.setConfigSchema(executor.getConfigSchema());
            tool.setStatus(STATUS_ENABLED);

            BuiltInTool existing = builtInToolMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BuiltInTool>()
                            .eq(BuiltInTool::getToolCode, toolCode)
            );
            if (existing == null) {
                builtInToolMapper.insert(tool);
            } else {
                tool.setId(existing.getId());
                builtInToolMapper.updateById(tool);
            }
        }
    }

    private void loadMcpServers() {
        List<McpServer> servers = mcpServerMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<McpServer>()
                        .eq(McpServer::getStatus, SERVER_STATUS_ACTIVE)
        );
        for (McpServer server : servers) {
            mcpServerMap.put(server.getServerName(), server);
            connectMcpServer(server);
        }
    }

    private void connectMcpServer(McpServer server) {
        try {
            mcpClientManager.connect(server);
            refreshMcpTools(server.getServerName());
            log.info("MCP Server 连接成功: {}", server.getServerName());
        } catch (Exception e) {
            log.error("MCP Server 连接失败: {}", server.getServerName(), e);
        }
    }

    private void refreshMcpTools(String serverName) {
        List<Map<String, Object>> tools = mcpClientManager.getServerTools(serverName);
        for (Map<String, Object> tool : tools) {
            String toolName = (String) tool.get(TOOL_KEY_NAME);
            String uniqueCode = serverName + TOOL_CODE_SEPARATOR + toolName;
            McpToolInfo info = new McpToolInfo();
            info.setToolName(toolName);
            info.setServerName(serverName);
            info.setDescription((String) tool.get(TOOL_KEY_DESCRIPTION));
            info.setInputSchema(tool.get(TOOL_KEY_INPUT_SCHEMA));
            mcpToolMap.put(uniqueCode, info);
        }
    }

    @Override
    public BuiltInToolExecutor getBuiltInTool(String toolCode) {
        return builtInToolMap.get(toolCode);
    }

    @Override
    public McpToolInfo getMcpTool(String toolCode) {
        return mcpToolMap.get(toolCode);
    }

    @Override
    public boolean hasTool(String toolCode) {
        return builtInToolMap.containsKey(toolCode) || mcpToolMap.containsKey(toolCode);
    }

    @Override
    public boolean isBuiltInTool(String toolCode) {
        return builtInToolMap.containsKey(toolCode);
    }

    @Override
    public boolean isMcpTool(String toolCode) {
        return mcpToolMap.containsKey(toolCode);
    }

    @Override
    public List<ToolInfoDTO> listAllTools() {
        List<ToolInfoDTO> tools = new ArrayList<>();

        for (BuiltInToolExecutor executor : builtInToolMap.values()) {
            ToolInfoDTO dto = new ToolInfoDTO();
            dto.setToolCode(executor.getToolCode());
            dto.setToolName(executor.getToolName());
            dto.setDescription(executor.getDescription());
            dto.setToolType(TOOL_TYPE_BUILT_IN);
            dto.setSource(SOURCE_BUILT_IN);
            try {
                dto.setConfigSchema(objectMapper.readTree(executor.getConfigSchema()));
            } catch (JsonProcessingException e) {
                dto.setConfigSchema(null);
            }
            tools.add(dto);
        }

        for (McpToolInfo mcpTool : mcpToolMap.values()) {
            ToolInfoDTO dto = new ToolInfoDTO();
            dto.setToolCode(mcpTool.getServerName() + TOOL_CODE_SEPARATOR + mcpTool.getToolName());
            dto.setToolName(mcpTool.getToolName());
            dto.setDescription(mcpTool.getDescription());
            dto.setToolType(TOOL_TYPE_MCP);
            dto.setSource(mcpTool.getServerName());
            dto.setConfigSchema(mcpTool.getInputSchema());
            tools.add(dto);
        }

        return tools;
    }

    @Override
    public List<ToolInfoDTO> listMcpTools() {
        return listAllTools().stream()
                .filter(t -> TOOL_TYPE_MCP.equals(t.getToolType()))
                .collect(Collectors.toList());
    }

    @Override
    public void refreshMcpServers() {
        mcpServerMap.clear();
        mcpToolMap.clear();
        mcpClientManager.closeAll();
        loadMcpServers();
    }

    @Override
    public void addMcpServer(McpServer server) {
        mcpServerMap.put(server.getServerName(), server);
        connectMcpServer(server);
    }

    @Override
    public void removeMcpServer(String serverName) {
        mcpServerMap.remove(serverName);
        mcpToolMap.entrySet().removeIf(entry -> entry.getValue().getServerName().equals(serverName));
        mcpClientManager.disconnect(serverName);
    }

    @Override
    public Map<String, String> getServerConnectionStatus() {
        Map<String, String> status = new HashMap<>();
        for (String serverName : mcpServerMap.keySet()) {
            boolean connected = mcpClientManager.isConnected(serverName);
            status.put(serverName, connected ? STATUS_CONNECTED : STATUS_DISCONNECTED);
        }
        return status;
    }
}
