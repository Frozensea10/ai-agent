package com.agent.mcp.client;

import com.agent.mcp.entity.McpServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MCP HTTP 客户端（SSE 传输模式）
 * 基于 MCP 协议规范实现：SSE 通道接收消息 + HTTP POST 发送命令
 * 支持 SSE 自动重连机制
 */
@Slf4j
public class McpHttpClient implements AutoCloseable {

    private final McpServer mcpServer;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicInteger requestId = new AtomicInteger(0);
    private final Map<String, CompletableFuture<JsonNode>> pendingRequests = new ConcurrentHashMap<>();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
    private final ScheduledExecutorService reconnectExecutor = Executors.newSingleThreadScheduledExecutor();

    private volatile String sseEndpoint;
    private volatile String messageEndpoint;
    private volatile boolean connected = false;
    private volatile boolean closed = false;
    private volatile Future<?> sseFuture;

    // 重连配置
    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    private static final int INITIAL_RECONNECT_DELAY_MS = 1000;
    private volatile int reconnectAttempts = 0;

    // 工具缓存
    private final List<Map<String, Object>> tools = new CopyOnWriteArrayList<>();

    public McpHttpClient(McpServer mcpServer) {
        this.mcpServer = mcpServer;
    }

    /**
     * 初始化连接：建立 SSE 连接并执行握手
     */
    public void initialize() throws Exception {
        if (connected || closed) {
            return;
        }

        JsonNode config = parseConfig();
        String baseUrl = config.has("baseUrl") ? config.get("baseUrl").asText() : "http://localhost:3000";
        sseEndpoint = baseUrl + "/sse";
        messageEndpoint = baseUrl + "/message";

        // 建立 SSE 连接
        connectSse();

        // 执行 MCP 握手
        JsonNode initResult = sendRequest("initialize", createInitializeParams());
        log.info("MCP Server 握手成功: {}", mcpServer.getServerName());

        // 发送 initialized 通知
        sendNotification("notifications/initialized", objectMapper.createObjectNode());

        // 获取工具列表
        discoverTools();

        connected = true;
        reconnectAttempts = 0;

        // 启动心跳
        startHeartbeat();
    }

    private JsonNode parseConfig() throws Exception {
        return objectMapper.readTree(mcpServer.getTransportConfig());
    }

    private void connectSse() throws Exception {
        URL url = new URL(sseEndpoint);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "text/event-stream");
        connection.setRequestProperty("Cache-Control", "no-cache");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(0); // SSE 长连接

        int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            throw new IOException("SSE 连接失败，HTTP状态码: " + responseCode);
        }

        sseFuture = executorService.submit(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                String currentEvent = "";
                StringBuilder dataBuffer = new StringBuilder();

                while (!closed && (line = reader.readLine()) != null) {
                    if (line.startsWith("event: ")) {
                        currentEvent = line.substring(7);
                    } else if (line.startsWith("data: ")) {
                        dataBuffer.append(line.substring(6));
                    } else if (line.isEmpty() && dataBuffer.length() > 0) {
                        String data = dataBuffer.toString();
                        dataBuffer.setLength(0);
                        handleSseEvent(currentEvent, data);
                        currentEvent = "";
                    }
                }
            } catch (IOException e) {
                if (!closed) {
                    log.error("SSE 连接异常: {}", mcpServer.getServerName(), e);
                    handleDisconnect();
                }
            }
        });

        // 等待 endpoint 事件
        boolean received = waitForEndpoint(5000);
        if (!received) {
            throw new IOException("未收到 SSE endpoint 事件");
        }
    }

    /**
     * 处理 SSE 连接断开，触发自动重连
     */
    private void handleDisconnect() {
        connected = false;
        if (closed) {
            return;
        }

        reconnectAttempts++;
        if (reconnectAttempts > MAX_RECONNECT_ATTEMPTS) {
            log.error("MCP Server {} 重连次数超过上限，放弃重连", mcpServer.getServerName());
            return;
        }

        int delay = Math.min(INITIAL_RECONNECT_DELAY_MS * (1 << (reconnectAttempts - 1)), 30000);
        log.info("MCP Server {} 将在 {}ms 后尝试第 {} 次重连", mcpServer.getServerName(), delay, reconnectAttempts);

        reconnectExecutor.schedule(() -> {
            try {
                log.info("MCP Server {} 开始重连...", mcpServer.getServerName());
                closeInternal();
                initialize();
                log.info("MCP Server {} 重连成功", mcpServer.getServerName());
            } catch (Exception e) {
                log.error("MCP Server {} 重连失败", mcpServer.getServerName(), e);
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    /**
     * 关闭内部资源（用于重连时清理）
     */
    private void closeInternal() {
        if (sseFuture != null) {
            sseFuture.cancel(true);
            sseFuture = null;
        }
        // 取消所有待处理的请求
        for (CompletableFuture<JsonNode> future : pendingRequests.values()) {
            future.cancel(true);
        }
        pendingRequests.clear();
    }

    private boolean waitForEndpoint(long timeoutMs) throws InterruptedException {
        long start = System.currentTimeMillis();
        while (messageEndpoint == null || messageEndpoint.endsWith("/message")) {
            if (System.currentTimeMillis() - start > timeoutMs) {
                return false;
            }
            Thread.sleep(100);
        }
        return true;
    }

    private void handleSseEvent(String event, String data) {
        try {
            if ("endpoint".equals(event)) {
                // 更新消息发送端点
                String endpoint = data.trim();
                if (!endpoint.startsWith("http")) {
                    // 相对路径，拼接 base URL
                    JsonNode config = parseConfig();
                    String baseUrl = config.has("baseUrl") ? config.get("baseUrl").asText() : "";
                    messageEndpoint = baseUrl + endpoint;
                } else {
                    messageEndpoint = endpoint;
                }
                log.debug("MCP SSE endpoint 更新: {}", messageEndpoint);
            } else if ("message".equals(event) || event.isEmpty()) {
                JsonNode message = objectMapper.readTree(data);
                handleJsonRpcMessage(message);
            }
        } catch (Exception e) {
            log.warn("处理 SSE 事件失败: {}", data, e);
        }
    }

    private void handleJsonRpcMessage(JsonNode message) {
        if (message.has("id")) {
            String id = message.get("id").asText();
            CompletableFuture<JsonNode> future = pendingRequests.remove(id);
            if (future != null) {
                if (message.has("error")) {
                    future.completeExceptionally(
                            new RuntimeException(message.get("error").toString()));
                } else {
                    future.complete(message.get("result"));
                }
            }
        }
    }

    private ObjectNode createInitializeParams() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("protocolVersion", "2024-11-05");

        ObjectNode capabilities = objectMapper.createObjectNode();
        ObjectNode clientCapabilities = objectMapper.createObjectNode();
        clientCapabilities.set("sampling", objectMapper.createObjectNode());
        clientCapabilities.set("roots", objectMapper.createObjectNode());
        capabilities.set("client", clientCapabilities);
        params.set("capabilities", capabilities);

        ObjectNode clientInfo = objectMapper.createObjectNode();
        clientInfo.put("name", "ai-agent-mcp-client");
        clientInfo.put("version", "1.0.0");
        params.set("clientInfo", clientInfo);

        return params;
    }

    /**
     * 发送 JSON-RPC 请求并等待响应
     */
    public JsonNode sendRequest(String method, JsonNode params) throws Exception {
        String id = String.valueOf(requestId.incrementAndGet());
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pendingRequests.put(id, future);

        ObjectNode request = objectMapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", id);
        request.put("method", method);
        request.set("params", params);

        sendHttpPost(request);

        return future.get(30, TimeUnit.SECONDS);
    }

    /**
     * 发送 JSON-RPC 通知（无需响应）
     */
    public void sendNotification(String method, JsonNode params) throws Exception {
        ObjectNode notification = objectMapper.createObjectNode();
        notification.put("jsonrpc", "2.0");
        notification.put("method", method);
        notification.set("params", params);

        sendHttpPost(notification);
    }

    private void sendHttpPost(JsonNode message) throws Exception {
        URL url = new URL(messageEndpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(30000);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(message.toString().getBytes(StandardCharsets.UTF_8));
        }

        int responseCode = conn.getResponseCode();
        if (responseCode != 200 && responseCode != 202) {
            throw new IOException("HTTP POST 失败，状态码: " + responseCode);
        }
    }

    /**
     * 发现 MCP Server 提供的工具列表
     */
    public void discoverTools() throws Exception {
        JsonNode result = sendRequest("tools/list", objectMapper.createObjectNode());

        tools.clear();
        if (result.has("tools") && result.get("tools").isArray()) {
            ArrayNode toolsArray = (ArrayNode) result.get("tools");
            for (JsonNode toolNode : toolsArray) {
                Map<String, Object> tool = new HashMap<>();
                tool.put("name", toolNode.get("name").asText());
                tool.put("description", toolNode.has("description") ? toolNode.get("description").asText() : "");
                tool.put("inputSchema", toolNode.has("inputSchema") ? toolNode.get("inputSchema") : objectMapper.createObjectNode());
                tools.add(tool);
            }
        }

        log.info("MCP Server {} 发现 {} 个工具", mcpServer.getServerName(), tools.size());
    }

    /**
     * 调用 MCP 工具
     */
    public JsonNode callTool(String toolName, Map<String, Object> arguments) throws Exception {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", toolName);

        ObjectNode argsNode = objectMapper.valueToTree(arguments);
        params.set("arguments", argsNode);

        JsonNode result = sendRequest("tools/call", params);

        if (result.has("isError") && result.get("isError").asBoolean()) {
            throw new RuntimeException("工具执行错误: " + result.get("content").toString());
        }

        return result;
    }

    public List<Map<String, Object>> getTools() {
        return new ArrayList<>(tools);
    }

    public boolean isConnected() {
        return connected;
    }

    private void startHeartbeat() {
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            try {
                if (!closed && connected) {
                    // 发送 ping 或保持连接
                    sendNotification("ping", objectMapper.createObjectNode());
                }
            } catch (Exception e) {
                log.warn("MCP 心跳失败: {}", mcpServer.getServerName(), e);
                connected = false;
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    @Override
    public void close() {
        closed = true;
        connected = false;

        closeInternal();

        executorService.shutdownNow();
        heartbeatExecutor.shutdownNow();
        reconnectExecutor.shutdownNow();

        log.info("MCP HTTP 客户端已关闭: {}", mcpServer.getServerName());
    }
}
