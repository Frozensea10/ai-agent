package com.agent.mcp.controller;

import com.agent.common.result.Result;
import com.agent.mcp.dto.McpServerDTO;
import com.agent.mcp.dto.ToolExecuteRequest;
import com.agent.mcp.dto.ToolExecuteResult;
import com.agent.mcp.dto.ToolInfoDTO;
import com.agent.mcp.service.McpServerService;
import com.agent.mcp.service.ToolExecutionService;
import com.agent.mcp.service.ToolRegistry;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/mcp")
@RequiredArgsConstructor
public class McpController {

    private final McpServerService mcpServerService;
    private final ToolExecutionService toolExecutionService;
    private final ToolRegistry toolRegistry;

    @GetMapping("/servers")
    public Result<List<McpServerDTO>> listServers() {
        return Result.success(mcpServerService.listServers());
    }

    @PostMapping("/servers")
    public Result<McpServerDTO> createServer(@Valid @RequestBody McpServerDTO dto) {
        return Result.success(mcpServerService.createServer(dto));
    }

    @PutMapping("/servers/{id}")
    public Result<Void> updateServer(@PathVariable Long id, @Valid @RequestBody McpServerDTO dto) {
        boolean success = mcpServerService.updateServer(id, dto);
        return success ? Result.success() : Result.error("更新失败，Server不存在");
    }

    @GetMapping("/servers/status")
    public Result<Map<String, String>> getServerConnectionStatus() {
        return Result.success(toolRegistry.getServerConnectionStatus());
    }

    @DeleteMapping("/servers/{id}")
    public Result<Void> deleteServer(@PathVariable Long id) {
        boolean success = mcpServerService.deleteServer(id);
        return success ? Result.success() : Result.error("删除失败，Server不存在");
    }

    @PutMapping("/servers/{id}/status")
    public Result<Void> updateServerStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        boolean success = mcpServerService.updateServerStatus(id, status);
        return success ? Result.success() : Result.error("更新失败，Server不存在");
    }

    @GetMapping("/tools")
    public Result<List<ToolInfoDTO>> listTools() {
        return Result.success(toolRegistry.listAllTools());
    }

    @PostMapping("/tools/{toolCode}/execute")
    public Result<ToolExecuteResult> executeTool(
            @PathVariable String toolCode,
            @RequestBody Map<String, Object> parameters) {
        ToolExecuteRequest request = new ToolExecuteRequest();
        request.setToolCode(toolCode);
        request.setParameters(parameters);
        return Result.success(toolExecutionService.execute(request));
    }

    @PostMapping("/tools/batch-execute")
    public Result<List<ToolExecuteResult>> batchExecuteTools(@RequestBody List<ToolExecuteRequest> requests) {
        List<ToolExecuteResult> results = requests.parallelStream()
                .map(toolExecutionService::execute)
                .toList();
        return Result.success(results);
    }
}
