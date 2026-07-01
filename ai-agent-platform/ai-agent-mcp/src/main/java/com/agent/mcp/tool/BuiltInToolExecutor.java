package com.agent.mcp.tool;

import com.agent.mcp.dto.ToolExecuteRequest;
import com.agent.mcp.dto.ToolExecuteResult;

public interface BuiltInToolExecutor {

    String getToolCode();

    String getToolName();

    String getDescription();

    String getConfigSchema();

    ToolExecuteResult execute(ToolExecuteRequest request);
}
