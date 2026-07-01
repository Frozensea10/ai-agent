package com.agent.mcp.client;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.mcp.entity.McpServer;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.agent.mcp.client.McpClientConstants.*;

/**
 * MCP 客户端管理器
 * 统一管理所有 MCP Server 的连接生命周期
 */
@Slf4j
@Component
public class McpClientManager {

    private final Map<String, McpHttpClient> httpClients = new ConcurrentHashMap<>();
    private final Map<String, McpStdioClient> stdioClients = new ConcurrentHashMap<>();

    /**
     * 创建并初始化 MCP 客户端
     */
    public void connect(McpServer server) {
        String serverName = server.getServerName();
        String serverType = server.getServerType();

        // 先断开已有连接
        disconnect(serverName);

        try {
            if (SERVER_TYPE_HTTP.equalsIgnoreCase(serverType) || SERVER_TYPE_SSE.equalsIgnoreCase(serverType)) {
                McpHttpClient client = new McpHttpClient(server);
                client.initialize();
                httpClients.put(serverName, client);
                log.info("MCP HTTP 客户端连接成功: {}", serverName);
            } else if (SERVER_TYPE_STDIO.equalsIgnoreCase(serverType)) {
                McpStdioClient client = new McpStdioClient(server);
                client.initialize();
                stdioClients.put(serverName, client);
                log.info("MCP Stdio 客户端连接成功: {}", serverName);
            } else {
                throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "不支持的 MCP Server 类型: " + serverType);
            }
        } catch (Exception e) {
            log.error("MCP 客户端连接失败: {}", serverName, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR.getCode(), "MCP 连接失败: " + e.getMessage(), e);
        }
    }

    /**
     * 断开指定 MCP Server 的连接
     */
    public void disconnect(String serverName) {
        McpHttpClient httpClient = httpClients.remove(serverName);
        if (httpClient != null) {
            try {
                httpClient.close();
                log.info("MCP HTTP 客户端已断开: {}", serverName);
            } catch (Exception e) {
                log.warn("关闭 MCP HTTP 客户端异常: {}", serverName, e);
            }
        }

        McpStdioClient stdioClient = stdioClients.remove(serverName);
        if (stdioClient != null) {
            try {
                stdioClient.close();
                log.info("MCP Stdio 客户端已断开: {}", serverName);
            } catch (Exception e) {
                log.warn("关闭 MCP Stdio 客户端异常: {}", serverName, e);
            }
        }
    }

    /**
     * 调用 MCP 工具
     */
    public JsonNode callTool(String serverName, String toolName, Map<String, Object> arguments) throws Exception {
        McpHttpClient httpClient = httpClients.get(serverName);
        if (httpClient != null && httpClient.isConnected()) {
            return httpClient.callTool(toolName, arguments);
        }

        McpStdioClient stdioClient = stdioClients.get(serverName);
        if (stdioClient != null && stdioClient.isConnected()) {
            return stdioClient.callTool(toolName, arguments);
        }

        throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "MCP Server 未连接: " + serverName);
    }

    /**
     * 获取指定 Server 的工具列表
     */
    public List<Map<String, Object>> getServerTools(String serverName) {
        McpHttpClient httpClient = httpClients.get(serverName);
        if (httpClient != null && httpClient.isConnected()) {
            return httpClient.getTools();
        }

        McpStdioClient stdioClient = stdioClients.get(serverName);
        if (stdioClient != null && stdioClient.isConnected()) {
            return stdioClient.getTools();
        }

        return List.of();
    }

    /**
     * 检查 Server 是否已连接
     */
    public boolean isConnected(String serverName) {
        McpHttpClient httpClient = httpClients.get(serverName);
        if (httpClient != null) {
            return httpClient.isConnected();
        }

        McpStdioClient stdioClient = stdioClients.get(serverName);
        if (stdioClient != null) {
            return stdioClient.isConnected();
        }

        return false;
    }

    /**
     * 获取所有已连接的 Server 名称
     */
    public List<String> getConnectedServers() {
        List<String> servers = new ArrayList<>();
        for (Map.Entry<String, McpHttpClient> entry : httpClients.entrySet()) {
            if (entry.getValue().isConnected()) {
                servers.add(entry.getKey());
            }
        }
        for (Map.Entry<String, McpStdioClient> entry : stdioClients.entrySet()) {
            if (entry.getValue().isConnected()) {
                servers.add(entry.getKey());
            }
        }
        return servers;
    }

    /**
     * 关闭所有连接
     */
    public void closeAll() {
        for (McpHttpClient client : httpClients.values()) {
            try {
                client.close();
            } catch (Exception e) {
                log.warn("关闭 MCP HTTP 客户端异常", e);
            }
        }
        httpClients.clear();

        for (McpStdioClient client : stdioClients.values()) {
            try {
                client.close();
            } catch (Exception e) {
                log.warn("关闭 MCP Stdio 客户端异常", e);
            }
        }
        stdioClients.clear();

        log.info("所有 MCP 客户端已关闭");
    }
}
