package com.agent.mcp.client;

public final class McpClientConstants {

    private McpClientConstants() {
    }

    public static final String SERVER_TYPE_HTTP = "http";
    public static final String SERVER_TYPE_SSE = "sse";
    public static final String SERVER_TYPE_STDIO = "stdio";

    public static final String TOOL_KEY_NAME = "name";
    public static final String TOOL_KEY_DESCRIPTION = "description";
    public static final String TOOL_KEY_INPUT_SCHEMA = "inputSchema";

    public static final String TOOL_CODE_SEPARATOR = "::";
}
