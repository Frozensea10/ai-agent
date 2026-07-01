package com.agent.chat.feign;

import com.agent.common.result.Result;
import com.agent.mcp.dto.ToolExecuteRequest;
import com.agent.mcp.dto.ToolExecuteResult;
import com.agent.mcp.dto.ToolInfoDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;

@FeignClient(name = "mcp-service", fallbackFactory = McpFeignClientFallbackFactory.class)
public interface McpFeignClient {

    @GetMapping("/api/v1/mcp/tools")
    Result<List<ToolInfoDTO>> listTools();

    @PostMapping("/api/v1/mcp/tools/{toolCode}/execute")
    Result<ToolExecuteResult> executeTool(@PathVariable("toolCode") String toolCode, @RequestBody Map<String, Object> parameters);
}
