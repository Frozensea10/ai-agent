package com.agent.mcp.client;

import com.agent.mcp.entity.McpServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MCP Stdio 客户端（标准输入输出传输模式）
 * 通过启动子进程，使用标准输入输出进行 JSON-RPC 通信
 *
 * <p>安全提示：本客户端会在宿主 JVM 中直接启动子进程执行外部命令，存在命令注入与
 * 任意系统命令执行风险。请务必：
 * <ul>
 *   <li>仅允许受信任的用户修改 {@code mcp_server.transport_config}</li>
 *   <li>命令名通过 {@link #ALLOWED_COMMANDS} 白名单校验</li>
 *   <li>环境变量通过 {@link #DANGEROUS_ENV_VARS} 黑名单过滤</li>
 *   <li>强烈建议配合 Docker 容器隔离运行 MCP Server，进一步限制文件系统 /
 *       网络 / 进程权限，避免子进程逃逸影响宿主机</li>
 * </ul>
 */
@Slf4j
public class McpStdioClient implements AutoCloseable {

    private final McpServer mcpServer;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicInteger requestId = new AtomicInteger(0);
    private final Map<String, CompletableFuture<JsonNode>> pendingRequests = new ConcurrentHashMap<>();
    private final ExecutorService readExecutor = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();

    private volatile Process process;
    private volatile BufferedWriter processWriter;
    private volatile BufferedReader processReader;
    private volatile boolean connected = false;
    private volatile boolean closed = false;

    /**
     * 允许启动的命令白名单（基于命令名匹配，不含路径）。修改此列表需经过安全评审。
     */
    private static final Set<String> ALLOWED_COMMANDS = Set.of(
            "npx", "node", "python", "python3", "uvx", "pipx");

    /**
     * 危险环境变量黑名单。这些变量可被用于劫持子进程行为（如加载恶意动态库、
     * 修改可执行文件搜索路径、注入 JVM 选项），在合并 env 时将被过滤。
     */
    private static final Set<String> DANGEROUS_ENV_VARS = Set.of(
            "LD_PRELOAD", "LD_LIBRARY_PATH", "PATH", "PYTHONPATH",
            "PYTHONHOME", "NODE_PATH", "JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS");

    // 工具缓存
    private final List<Map<String, Object>> tools = new CopyOnWriteArrayList<>();

    public McpStdioClient(McpServer mcpServer) {
        this.mcpServer = mcpServer;
    }

    /**
     * 初始化连接：启动子进程并执行握手
     */
    public void initialize() throws Exception {
        if (connected || closed) {
            return;
        }

        JsonNode config = parseConfig();
        String command = config.has("command") ? config.get("command").asText() : "";
        ArrayNode argsArray = config.has("args") && config.get("args").isArray() ? (ArrayNode) config.get("args") : null;

        if (command.isEmpty()) {
            throw new IllegalArgumentException("Stdio 配置缺少 command 字段");
        }

        // 命令白名单校验：防止任意可执行文件被启动（如 rm / sh / curl）
        String commandBase = commandBasename(command);
        if (!ALLOWED_COMMANDS.contains(commandBase)) {
            throw new SecurityException(
                    "禁止启动非白名单命令: " + command + "，允许的命令: " + ALLOWED_COMMANDS);
        }

        // 构建进程启动命令
        List<String> cmdList = new ArrayList<>();
        cmdList.add(command);
        if (argsArray != null) {
            for (JsonNode arg : argsArray) {
                cmdList.add(arg.asText());
            }
        }

        // 启动子进程
        ProcessBuilder pb = new ProcessBuilder(cmdList);
        pb.redirectErrorStream(true); // 合并 stderr 到 stdout
        // 限定工作目录到临时目录，避免子进程在业务目录读写敏感文件
        pb.directory(new File(System.getProperty("java.io.tmpdir")));

        // 环境变量（过滤危险变量）
        if (config.has("env") && config.get("env").isObject()) {
            JsonNode envNode = config.get("env");
            Map<String, String> env = pb.environment();
            envNode.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                if (DANGEROUS_ENV_VARS.contains(key)) {
                    log.warn("过滤危险环境变量: {} (server={})", key, mcpServer.getServerName());
                    return;
                }
                env.put(key, entry.getValue().asText());
            });
        }

        process = pb.start();
        processWriter = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        processReader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

        // 启动读取线程
        readExecutor.submit(this::readLoop);

        // 执行 MCP 握手
        JsonNode initResult = sendRequest("initialize", createInitializeParams());
        log.info("MCP Stdio Server 握手成功: {}", mcpServer.getServerName());

        // 发送 initialized 通知
        sendNotification("notifications/initialized", objectMapper.createObjectNode());

        // 获取工具列表
        discoverTools();

        connected = true;

        // 启动进程监控
        startProcessMonitor();

        log.info("MCP Stdio 客户端初始化完成: {}, 命令: {}", mcpServer.getServerName(), command);
    }

    private JsonNode parseConfig() throws Exception {
        return objectMapper.readTree(mcpServer.getTransportConfig());
    }

    /**
     * 提取命令的基础名称（去除路径前缀），用于白名单匹配。
     * 例如 "/usr/local/bin/npx" -> "npx"，"npx.cmd" -> "npx"，"python3.exe" -> "python3"
     */
    private static String commandBasename(String command) {
        if (command == null || command.isEmpty()) {
            return "";
        }
        String name = command;
        int slash = Math.max(command.lastIndexOf('/'), command.lastIndexOf('\\'));
        if (slash >= 0 && slash < command.length() - 1) {
            name = command.substring(slash + 1);
        }
        // 去除 Windows 可执行文件后缀（.exe / .cmd / .bat）
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        return name;
    }

    private void readLoop() {
        try {
            String line;
            while (!closed && (line = processReader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                try {
                    JsonNode message = objectMapper.readTree(line);
                    handleJsonRpcMessage(message);
                } catch (Exception e) {
                    log.warn("解析 JSON-RPC 消息失败: {}", line, e);
                }
            }
        } catch (IOException e) {
            if (!closed) {
                log.error("读取子进程输出异常: {}", mcpServer.getServerName(), e);
                connected = false;
            }
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

        sendMessage(request);

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

        sendMessage(notification);
    }

    private synchronized void sendMessage(JsonNode message) throws Exception {
        if (processWriter == null) {
            throw new IOException("进程未启动");
        }
        String json = message.toString();
        processWriter.write(json);
        processWriter.newLine();
        processWriter.flush();
        log.debug("MCP Stdio 发送: {}", json);
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

        log.info("MCP Stdio Server {} 发现 {} 个工具", mcpServer.getServerName(), tools.size());
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
        return connected && process != null && process.isAlive();
    }

    private void startProcessMonitor() {
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            try {
                if (!closed && process != null && !process.isAlive()) {
                    log.warn("MCP Stdio 进程已退出: {}", mcpServer.getServerName());
                    connected = false;
                    // 触发清理：关闭 reader/writer，使 readLoop 退出并阻止后续写入
                    cleanupStreams();
                }
            } catch (Exception e) {
                log.warn("进程监控异常: {}", mcpServer.getServerName(), e);
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

    /**
     * 关闭子进程读写流，用于进程异常退出后的清理。不会关闭整个客户端。
     */
    private void cleanupStreams() {
        BufferedWriter w = processWriter;
        if (w != null) {
            try {
                w.close();
            } catch (IOException e) {
                log.warn("清理时关闭进程写入流失败", e);
            }
        }
        BufferedReader r = processReader;
        if (r != null) {
            try {
                r.close();
            } catch (IOException e) {
                log.warn("清理时关闭进程读取流失败", e);
            }
        }
    }

    @Override
    public void close() {
        closed = true;
        connected = false;

        // 取消所有待处理的请求
        for (CompletableFuture<JsonNode> future : pendingRequests.values()) {
            future.cancel(true);
        }
        pendingRequests.clear();

        // 1. 先强制销毁子进程，避免其继续向流中写入数据
        if (process != null) {
            process.destroyForcibly();
        }

        // 2. 关闭读写流
        if (processWriter != null) {
            try {
                processWriter.close();
            } catch (IOException e) {
                log.warn("关闭进程写入流失败", e);
            }
        }

        if (processReader != null) {
            try {
                processReader.close();
            } catch (IOException e) {
                log.warn("关闭进程读取流失败", e);
            }
        }

        // 3. 最后关闭线程池（读取线程可能阻塞在 readLine，需在流关闭后再 shutdownNow）
        readExecutor.shutdownNow();
        heartbeatExecutor.shutdownNow();

        log.info("MCP Stdio 客户端已关闭: {}", mcpServer.getServerName());
    }
}
