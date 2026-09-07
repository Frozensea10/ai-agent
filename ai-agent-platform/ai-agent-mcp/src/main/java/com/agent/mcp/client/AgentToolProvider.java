package com.agent.mcp.client;

import com.agent.mcp.dto.ToolExecuteRequest;
import com.agent.mcp.dto.ToolInfoDTO;
import com.agent.mcp.service.ToolExecutionService;
import com.agent.mcp.service.ToolRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 工具提供者
 * 将外部 MCP Server 和内置工具统一注册为 LangChain4j ToolProvider，供 AiServices 使用
 */
@Slf4j
@Component
public class AgentToolProvider implements ToolProvider {

    /** 内置工具类型标识。 */
    private static final String BUILT_IN_TYPE = "built_in";
    /** 工具名称分隔符（MCP 工具命名格式：serverName__toolName）。 */
    private static final String TOOL_NAME_SEPARATOR = "__";
    /** 工具名称清洗正则：将不合法字符替换为下划线（LangChain4j 要求名称仅含字母数字、下划线与连字符）。 */
    private static final java.util.regex.Pattern TOOL_NAME_SANITIZE_PATTERN = java.util.regex.Pattern.compile("[^a-zA-Z0-9_-]");

    private final McpClientManager mcpClientManager;
    private final ToolRegistry toolRegistry;
    private final ToolExecutionService toolExecutionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 构造 Agent 工具提供者。
     *
     * @param mcpClientManager     MCP 客户端管理器
     * @param toolRegistry         工具注册表
     * @param toolExecutionService 工具执行服务
     */
    public AgentToolProvider(McpClientManager mcpClientManager, ToolRegistry toolRegistry,
                             ToolExecutionService toolExecutionService) {
        this.mcpClientManager = mcpClientManager;
        this.toolRegistry = toolRegistry;
        this.toolExecutionService = toolExecutionService;
    }

    /**
     * 向 LangChain4j 提供全部可用工具（MCP 工具 + 内置工具）。
     *
     * @param request 工具提供请求
     * @return 工具规范与执行器的映射结果
     */
    @Override
    public ToolProviderResult provideTools(ToolProviderRequest request) {
        Map<ToolSpecification, ToolExecutor> tools = new HashMap<>();
        // 注册外部 MCP Server 的工具
        registerMcpTools(tools);
        // 注册内置工具
        registerBuiltInTools(tools);// 注册内置工具
        log.info("AgentToolProvider 共注册 {} 个工具", tools.size());
        return ToolProviderResult.builder().addAll(tools).build();
    }

    /**
     * 注册所有已连接 MCP Server 的工具到工具映射。
     *
     * @param tools 工具规范 -> 执行器的映射容器
     */
    private void registerMcpTools(Map<ToolSpecification, ToolExecutor> tools) {
        for (String serverName : mcpClientManager.getConnectedServers()) {
            List<Map<String, Object>> serverTools = mcpClientManager.getServerTools(serverName);
            for (Map<String, Object> tool : serverTools) {
                try {
                    String toolName = (String) tool.get("name");
                    ToolSpecification spec = buildMcpToolSpec(serverName, toolName,
                            (String) tool.get("description"), tool.get("inputSchema"));
                    tools.put(spec, createMcpExecutor(serverName, toolName));
                } catch (Exception e) {
                    log.warn("注册 MCP 工具失败: {}.{}", serverName, tool.get("name"), e);
                }
            }
        }
    }

    /**
     * 注册所有内置工具到工具映射。
     *
     * @param tools 工具规范 -> 执行器的映射容器
     */
    private void registerBuiltInTools(Map<ToolSpecification, ToolExecutor> tools) {
        for (ToolInfoDTO toolInfo : toolRegistry.listAllTools()) {
            if (!BUILT_IN_TYPE.equals(toolInfo.getToolType())) {
                continue;
            }
            try {
                ToolSpecification spec = buildBuiltInToolSpec(toolInfo);
                tools.put(spec, createBuiltInExecutor(toolInfo.getToolCode()));
            } catch (Exception e) {
                log.warn("注册内置工具失败: {}", toolInfo.getToolCode(), e);
            }
        }
    }

    /**
     * 构建 MCP 工具的 LangChain4j 规范（名称带 Server 前缀，描述带来源标记）。
     */
    private ToolSpecification buildMcpToolSpec(String serverName, String toolName,
                                                String description, Object schemaObj) {
        return ToolSpecification.builder()
                .name(sanitizeToolName(serverName + TOOL_NAME_SEPARATOR + toolName))
                .description("[" + serverName + "] " + (description != null ? description : ""))
                .parameters(parseJsonSchema(schemaObj))
                .build();
    }

    /**
     * 清洗工具名称：将不符合 LangChain4j 命名要求的字符替换为下划线。
     */
    private String sanitizeToolName(String name) {
        return TOOL_NAME_SANITIZE_PATTERN.matcher(name).replaceAll("_");
    }

    /**
     * 构建内置工具的 LangChain4j 规范。
     */
    private ToolSpecification buildBuiltInToolSpec(ToolInfoDTO toolInfo) {
        return ToolSpecification.builder()
                .name(sanitizeToolName(toolInfo.getToolCode()))
                .description(toolInfo.getDescription() != null ? toolInfo.getDescription() : "")
                .parameters(parseJsonSchema(toolInfo.getConfigSchema()))
                .build();
    }

    /**
     * 创建 MCP 工具执行器：解析参数后经 {@link ToolExecutionService} 统一执行。
     */
    private ToolExecutor createMcpExecutor(String serverName, String toolName) {
        return (request, memoryId) -> {
            try {
                Map<String, Object> args = objectMapper.readValue(request.arguments(), Map.class);
                ToolExecuteRequest execRequest = new ToolExecuteRequest();
                execRequest.setToolCode(serverName + McpClientConstants.TOOL_CODE_SEPARATOR + toolName);
                execRequest.setParameters(args);
                var result = toolExecutionService.execute(execRequest);
                return result.getData() != null ? result.getData().toString() : "Success";
            } catch (Exception e) {
                log.error("MCP 工具执行失败: {}.{}", serverName, toolName, e);
                return "Error: " + e.getMessage();
            }
        };
    }

    /**
     * 创建内置工具执行器：解析参数后经 {@link ToolExecutionService} 统一执行。
     */
    private ToolExecutor createBuiltInExecutor(String toolCode) {
        return (request, memoryId) -> {
            try {
                Map<String, Object> args = objectMapper.readValue(request.arguments(), Map.class);
                ToolExecuteRequest execRequest = new ToolExecuteRequest();
                execRequest.setToolCode(toolCode);
                execRequest.setParameters(args);
                var result = toolExecutionService.execute(execRequest);
                return result.getData() != null ? result.getData().toString() : "Success";
            } catch (Exception e) {
                log.error("内置工具执行失败: {}", toolCode, e);
                return "Error: " + e.getMessage();
            }
        };
    }

    /**
     * 将 JSON Schema 对象解析为 LangChain4j 的 JsonObjectSchema，解析失败时返回空 Schema。
     */
    private JsonObjectSchema parseJsonSchema(Object schemaObj) {
        if (schemaObj == null) {
            return JsonObjectSchema.builder().build();
        }
        try {
            JsonNode schemaNode = objectMapper.valueToTree(schemaObj);
            if (schemaNode == null || !schemaNode.isObject()) {
                return JsonObjectSchema.builder().build();
            }
            return buildObjectSchema(schemaNode);
        } catch (Exception e) {
            log.warn("解析 JSON Schema 失败", e);
            return JsonObjectSchema.builder().build();
        }
    }

    /**
     * 构建对象级 Schema：遍历 properties，并按 required 列表标记必填属性。
     */
    private JsonObjectSchema buildObjectSchema(JsonNode schemaNode) {
        JsonObjectSchema.Builder builder = JsonObjectSchema.builder();
        JsonNode properties = schemaNode.get("properties");
        JsonNode required = schemaNode.get("required");

        if (properties != null && properties.isObject()) {
            properties.fields().forEachRemaining(entry -> {
                String propName = entry.getKey();
                boolean isRequired = required != null && required.isArray()
                        && containsValue(required, propName);
                builder.addProperty(propName, parseProperty(entry.getValue()));
                if (isRequired) {
                    builder.required(propName);
                }
            });
        }
        return builder.build();
    }

    /**
     * 判断 JSON 数组中是否包含指定字符串值。
     */
    private boolean containsValue(JsonNode array, String value) {
        for (JsonNode node : array) {
            if (node.asText().equals(value)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 将单个属性的 JSON Schema 按类型映射为 LangChain4j 的 Schema 元素（默认按字符串处理）。
     */
    private JsonSchemaElement parseProperty(JsonNode propSchema) {
        String type = propSchema.has("type") ? propSchema.get("type").asText() : "string";
        String description = propSchema.has("description") ? propSchema.get("description").asText() : "";

        return switch (type) {
            case "integer", "number" -> dev.langchain4j.model.chat.request.json.JsonIntegerSchema.builder()
                    .description(description).build();
            case "boolean" -> dev.langchain4j.model.chat.request.json.JsonBooleanSchema.builder()
                    .description(description).build();
            case "array" -> dev.langchain4j.model.chat.request.json.JsonArraySchema.builder()
                    .description(description)
                    .items(dev.langchain4j.model.chat.request.json.JsonStringSchema.builder().build())
                    .build();
            case "object" -> dev.langchain4j.model.chat.request.json.JsonObjectSchema.builder()
                    .description(description).build();
            default -> dev.langchain4j.model.chat.request.json.JsonStringSchema.builder()
                    .description(description).build();
        };
    }
}
