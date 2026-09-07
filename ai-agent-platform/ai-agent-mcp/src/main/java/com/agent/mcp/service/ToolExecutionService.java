package com.agent.mcp.service;

import com.agent.mcp.dto.ToolExecuteRequest;
import com.agent.mcp.dto.ToolExecuteResult;

public interface ToolExecutionService {

    ToolExecuteResult execute(ToolExecuteRequest request);
}
