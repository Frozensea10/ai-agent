package com.agent.mcp.service;

import com.agent.mcp.client.McpClientManager;
import com.agent.mcp.dto.ToolExecuteRequest;
import com.agent.mcp.dto.ToolExecuteResult;
import com.agent.mcp.entity.ToolExecutionLog;
import com.agent.mcp.mapper.ToolExecutionLogMapper;
import com.agent.mcp.tool.BuiltInToolExecutor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ToolExecutionService {

    private final ToolRegistry toolRegistry;
    private final ToolExecutionLogMapper toolExecutionLogMapper;
    private final McpClientManager mcpClientManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 敏感工具列表：其参数可能包含 SQL、代码、密码等敏感信息，需脱敏后落库
     */
    private static final Set<String> SENSITIVE_TOOL_CODES = Set.of("code_java", "code_python", "db_query");

    public ToolExecuteResult execute(ToolExecuteRequest request) {
        String toolCode = request.getToolCode();
        log.info("执行工具: {}", toolCode);

        ToolExecutionLog logEntity = new ToolExecutionLog();
        logEntity.setToolCode(toolCode);
        logEntity.setStatus("running");

        long startTime = System.currentTimeMillis();

        try {
            if (toolRegistry.isBuiltInTool(toolCode)) {
                logEntity.setToolType("built_in");
                return executeBuiltInTool(request, logEntity, startTime);
            } else if (toolRegistry.isMcpTool(toolCode)) {
                logEntity.setToolType("mcp");
                return executeMcpTool(request, logEntity, startTime);
            } else {
                return handleToolNotFound(toolCode, logEntity, startTime);
            }
        } catch (Exception e) {
            log.error("工具执行异常: {}", toolCode, e);
            return handleExecutionError(toolCode, logEntity, startTime, e);
        }
    }

    private ToolExecuteResult executeBuiltInTool(ToolExecuteRequest request, ToolExecutionLog logEntity, long startTime) {
        String toolCode = request.getToolCode();
        BuiltInToolExecutor executor = toolRegistry.getBuiltInTool(toolCode);
        if (executor == null) {
            return handleToolNotFound(toolCode, logEntity, startTime);
        }

        logEntity.setRequestParams(maskSensitiveParams(toolCode, request.getParameters()));

        ToolExecuteResult result = executor.execute(request);

        long executeTime = System.currentTimeMillis() - startTime;
        result.setExecuteTimeMs(executeTime);

        logEntity.setStatus(result.isSuccess() ? "success" : "failed");
        logEntity.setResponseResult(result.getData() != null ? result.getData().toString() : null);
        logEntity.setErrorMessage(result.getErrorMessage());
        logEntity.setExecuteTimeMs(executeTime);
        toolExecutionLogMapper.insert(logEntity);

        log.info("内置工具执行完成: {}, 成功: {}, 耗时: {}ms", toolCode, result.isSuccess(), executeTime);
        return result;
    }

    private ToolExecuteResult executeMcpTool(ToolExecuteRequest request, ToolExecutionLog logEntity, long startTime) {
        String toolCode = request.getToolCode();
        ToolRegistry.McpToolInfo mcpTool = toolRegistry.getMcpTool(toolCode);

        if (mcpTool == null) {
            return handleToolNotFound(toolCode, logEntity, startTime);
        }

        String serverName = mcpTool.getServerName();
        String toolName = mcpTool.getToolName();
        Map<String, Object> parameters = request.getParameters();

        logEntity.setRequestParams(maskSensitiveParams(toolCode, parameters));

        try {
            JsonNode result = mcpClientManager.callTool(serverName, toolName, parameters);

            long executeTime = System.currentTimeMillis() - startTime;

            ToolExecuteResult executeResult = new ToolExecuteResult();
            executeResult.setSuccess(true);
            executeResult.setToolCode(toolCode);
            executeResult.setData(objectMapper.writeValueAsString(result));
            executeResult.setExecuteTimeMs(executeTime);

            logEntity.setStatus("success");
            logEntity.setResponseResult(executeResult.getData().toString());
            logEntity.setExecuteTimeMs(executeTime);
            toolExecutionLogMapper.insert(logEntity);

            log.info("MCP 工具执行完成: {}.{}, 成功: true, 耗时: {}ms", serverName, toolName, executeTime);
            return executeResult;

        } catch (Exception e) {
            long executeTime = System.currentTimeMillis() - startTime;

            ToolExecuteResult executeResult = new ToolExecuteResult();
            executeResult.setSuccess(false);
            executeResult.setToolCode(toolCode);
            executeResult.setErrorMessage("MCP 工具执行失败: " + e.getMessage());
            executeResult.setExecuteTimeMs(executeTime);

            logEntity.setStatus("failed");
            logEntity.setErrorMessage(e.getMessage());
            logEntity.setExecuteTimeMs(executeTime);
            toolExecutionLogMapper.insert(logEntity);

            log.error("MCP 工具执行失败: {}.{}", serverName, toolName, e);
            return executeResult;
        }
    }

    private ToolExecuteResult handleToolNotFound(String toolCode, ToolExecutionLog logEntity, long startTime) {
        long executeTime = System.currentTimeMillis() - startTime;

        ToolExecuteResult result = new ToolExecuteResult();
        result.setSuccess(false);
        result.setToolCode(toolCode);
        result.setErrorMessage("工具不存在: " + toolCode);
        result.setExecuteTimeMs(executeTime);

        logEntity.setToolType("unknown");
        logEntity.setStatus("failed");
        logEntity.setErrorMessage(result.getErrorMessage());
        logEntity.setExecuteTimeMs(executeTime);
        toolExecutionLogMapper.insert(logEntity);

        return result;
    }

    private ToolExecuteResult handleExecutionError(String toolCode, ToolExecutionLog logEntity, long startTime, Exception e) {
        long executeTime = System.currentTimeMillis() - startTime;

        ToolExecuteResult result = new ToolExecuteResult();
        result.setSuccess(false);
        result.setToolCode(toolCode);
        result.setErrorMessage("执行异常: " + e.getMessage());
        result.setExecuteTimeMs(executeTime);

        logEntity.setStatus("failed");
        logEntity.setErrorMessage(e.getMessage());
        logEntity.setExecuteTimeMs(executeTime);
        toolExecutionLogMapper.insert(logEntity);

        return result;
    }

    /**
     * 对敏感工具的参数做脱敏处理，避免 SQL、代码、密码等敏感信息落库。
     * 非敏感工具保留原始参数字符串用于问题排查。
     */
    private String maskSensitiveParams(String toolCode, Map<String, Object> parameters) {
        if (parameters == null) {
            return "{}";
        }
        String paramsStr = parameters.toString();
        if (SENSITIVE_TOOL_CODES.contains(toolCode)) {
            return "[REDACTED, paramCount=" + parameters.size() + ", length=" + paramsStr.length() + "]";
        }
        return paramsStr;
    }
}
