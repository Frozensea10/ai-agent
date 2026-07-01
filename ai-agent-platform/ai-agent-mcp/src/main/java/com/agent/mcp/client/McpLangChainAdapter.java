package com.agent.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * LangChain4j MCP 工具适配器
 * 将 MCP 工具转换为 LangChain4j 的 Tool 规范，供 LLM Agent 使用
 */
@Slf4j
public class McpLangChainAdapter {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final McpClientManager clientManager;

    public McpLangChainAdapter(McpClientManager clientManager) {
        this.clientManager = clientManager;
    }

    /**
     * 获取所有可用的 LangChain4j ToolSpecification
     */
    public List<dev.langchain4j.agent.tool.ToolSpecification> getAllToolSpecifications() {
        List<dev.langchain4j.agent.tool.ToolSpecification> specs = new ArrayList<>();

        for (String serverName : clientManager.getConnectedServers()) {
            List<Map<String, Object>> tools = clientManager.getServerTools(serverName);
            for (Map<String, Object> tool : tools) {
                try {
                    dev.langchain4j.agent.tool.ToolSpecification spec = convertToToolSpecification(serverName, tool);
                    specs.add(spec);
                } catch (Exception e) {
                    log.warn("转换 MCP 工具为 ToolSpecification 失败: {}.{}", serverName, tool.get("name"), e);
                }
            }
        }

        return specs;
    }

    /**
     * 将 MCP 工具定义转换为 LangChain4j ToolSpecification
     */
    private dev.langchain4j.agent.tool.ToolSpecification convertToToolSpecification(String serverName, Map<String, Object> tool) {
        String toolName = (String) tool.get("name");
        String description = (String) tool.get("description");
        Object schemaObj = tool.get("inputSchema");

        String uniqueToolName = serverName + "::" + toolName;

        dev.langchain4j.model.chat.request.json.JsonObjectSchema parametersSchema = parseJsonSchema(schemaObj);

        return dev.langchain4j.agent.tool.ToolSpecification.builder()
                .name(uniqueToolName)
                .description("[" + serverName + "] " + description)
                .parameters(parametersSchema)
                .build();
    }

    /**
     * 解析 JSON Schema 为 LangChain4j 参数模式
     */
    private dev.langchain4j.model.chat.request.json.JsonObjectSchema parseJsonSchema(Object schemaObj) {
        try {
            JsonNode schemaNode = objectMapper.valueToTree(schemaObj);
            if (schemaNode == null || !schemaNode.isObject()) {
                return dev.langchain4j.model.chat.request.json.JsonObjectSchema.builder().build();
            }

            dev.langchain4j.model.chat.request.json.JsonObjectSchema.Builder builder =
                    dev.langchain4j.model.chat.request.json.JsonObjectSchema.builder();

            JsonNode properties = schemaNode.get("properties");
            JsonNode required = schemaNode.get("required");

            if (properties != null && properties.isObject()) {
                properties.fields().forEachRemaining(entry -> {
                    String propName = entry.getKey();
                    JsonNode propSchema = entry.getValue();
                    boolean isRequired = required != null && required.isArray() && containsNode(required, propName);

                    dev.langchain4j.model.chat.request.json.JsonSchemaElement element = parsePropertySchema(propSchema);
                    builder.addProperty(propName, element);
                    if (isRequired) {
                        builder.required(propName);
                    }
                });
            }

            return builder.build();
        } catch (Exception e) {
            log.warn("解析 JSON Schema 失败", e);
            return dev.langchain4j.model.chat.request.json.JsonObjectSchema.builder().build();
        }
    }

    private boolean containsNode(JsonNode array, String value) {
        for (JsonNode node : array) {
            if (node.asText().equals(value)) {
                return true;
            }
        }
        return false;
    }

    private dev.langchain4j.model.chat.request.json.JsonSchemaElement parsePropertySchema(JsonNode propSchema) {
        String type = propSchema.has("type") ? propSchema.get("type").asText() : "string";
        String description = propSchema.has("description") ? propSchema.get("description").asText() : "";

        return switch (type) {
            case "string" -> dev.langchain4j.model.chat.request.json.JsonStringSchema.builder()
                    .description(description)
                    .build();
            case "integer", "number" -> dev.langchain4j.model.chat.request.json.JsonIntegerSchema.builder()
                    .description(description)
                    .build();
            case "boolean" -> dev.langchain4j.model.chat.request.json.JsonBooleanSchema.builder()
                    .description(description)
                    .build();
            case "array" -> dev.langchain4j.model.chat.request.json.JsonArraySchema.builder()
                    .description(description)
                    .items(dev.langchain4j.model.chat.request.json.JsonStringSchema.builder().build())
                    .build();
            case "object" -> dev.langchain4j.model.chat.request.json.JsonObjectSchema.builder()
                    .description(description)
                    .build();
            default -> dev.langchain4j.model.chat.request.json.JsonStringSchema.builder()
                    .description(description)
                    .build();
        };
    }

    /**
     * 执行 MCP 工具调用（供 LangChain4j Agent 使用）
     * 工具名称格式: serverName::toolName（使用 :: 分隔避免与工具名中的 _ 冲突）
     */
    public String executeTool(dev.langchain4j.agent.tool.ToolExecutionRequest toolExecutionRequest) {
        String uniqueToolName = toolExecutionRequest.name();
        String arguments = toolExecutionRequest.arguments();

        // 使用 :: 作为分隔符，避免与工具名中的 _ 冲突
        int separatorIndex = uniqueToolName.indexOf("::");
        if (separatorIndex <= 0 || separatorIndex >= uniqueToolName.length() - 2) {
            return "Error: Invalid tool name format: " + uniqueToolName;
        }

        String serverName = uniqueToolName.substring(0, separatorIndex);
        String toolName = uniqueToolName.substring(separatorIndex + 2);

        try {
            Map<String, Object> argsMap = objectMapper.readValue(arguments, Map.class);
            JsonNode result = clientManager.callTool(serverName, toolName, argsMap);
            return result.toString();
        } catch (Exception e) {
            log.error("MCP 工具执行失败: {}.{}", serverName, toolName, e);
            return "Error: " + e.getMessage();
        }
    }

    /**
     * 创建 LangChain4j 的 ToolSpecification 列表
     */
    public List<dev.langchain4j.agent.tool.ToolSpecification> createToolSpecifications() {
        return getAllToolSpecifications();
    }
}
