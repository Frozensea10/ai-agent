package com.agent.mcp.controller;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.common.result.Result;
import com.agent.mcp.dto.McpServerDTO;
import com.agent.mcp.dto.ToolExecuteRequest;
import com.agent.mcp.dto.ToolExecuteResult;
import com.agent.mcp.dto.ToolInfoDTO;
import com.agent.mcp.service.McpServerService;
import com.agent.mcp.service.ToolExecutionService;
import com.agent.mcp.service.ToolRegistry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MCP 工具管理接口。
 *
 * <p>权限模型：
 * <ul>
 *   <li>所有工具执行接口均要求经过网关认证（网关校验 JWT 后透传 {@code X-User-Id} 头），
 *       Controller 通过 {@code @RequestHeader("X-User-Id")} 获取调用者身份并防御性校验，
 *       避免下游服务端口被直连绕过网关。</li>
 *   <li>普通工具（内置非高危工具、MCP 工具）：任意已认证用户可执行（对应权限 {@code mcp:execute}）。</li>
 *   <li>高危工具（{@link #DANGEROUS_TOOL_CODES}，含 {@code code_java}/{@code code_python}/
 *       {@code db_query}/{@code http_request}）：仅允许配置在 {@code mcp.admin.user-ids}
 *       中的管理员用户执行（对应权限 {@code mcp:execute:dangerous}）。当前 JWT 不携带角色声明，
 *       暂以可配置的管理员用户 ID 白名单实现；后续接入 RBAC 后可替换为
 *       {@code @PreAuthorize("hasAuthority('mcp:execute:dangerous')")}。</li>
 *   <li>批量执行：单次最多 {@value #MAX_BATCH_SIZE} 个工具，串行执行以避免
 *       占用 {@code ForkJoinPool.commonPool()} 与并发写入日志表耗尽 DB 连接池。</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/mcp")
@RequiredArgsConstructor
@Validated
public class McpController {

    /** 高危工具代码集合，执行需管理员权限（mcp:execute:dangerous）。 */
    private static final Set<String> DANGEROUS_TOOL_CODES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("code_java", "code_python", "db_query", "http_request")));

    /** 批量执行单次最大数量。 */
    private static final int MAX_BATCH_SIZE = 10;

    private final McpServerService mcpServerService;
    private final ToolExecutionService toolExecutionService;
    private final ToolRegistry toolRegistry;

    /** 高危工具执行允许的管理员用户 ID 集合，通过 {@code mcp.admin.user-ids} 配置（逗号分隔）。 */
    @Value("${mcp.admin.user-ids:}")
    private String adminUserIds;

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
            @RequestBody Map<String, Object> parameters,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        checkExecutePermission(userId, toolCode);
        ToolExecuteRequest request = new ToolExecuteRequest();
        request.setToolCode(toolCode);
        request.setParameters(parameters);
        return Result.success(toolExecutionService.execute(request));
    }

    @PostMapping("/tools/batch-execute")
    public Result<List<ToolExecuteResult>> batchExecuteTools(
            @Valid @Size(max = MAX_BATCH_SIZE, message = "单次批量执行最多 10 个工具") @RequestBody List<ToolExecuteRequest> requests,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        // 串行执行：避免占用 ForkJoinPool.commonPool() 及并发写入 toolExecutionLog 造成 DB 连接池耗尽
        List<ToolExecuteResult> results = requests.stream()
                .map(req -> {
                    checkExecutePermission(userId, req.getToolCode());
                    return toolExecutionService.execute(req);
                })
                .toList();
        return Result.success(results);
    }

    /**
     * 校验工具执行权限：已认证用户可执行普通工具；高危工具仅管理员可执行。
     *
     * @throws BusinessException 未认证 ({@link ErrorCode#UNAUTHORIZED}) 或无高危工具执行权限 ({@link ErrorCode#FORBIDDEN})
     */
    private void checkExecutePermission(Long userId, String toolCode) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未认证：缺少用户身份信息");
        }
        if (toolCode != null && DANGEROUS_TOOL_CODES.contains(toolCode)) {
            if (!isAdmin(userId)) {
                log.warn("用户 {} 无高危工具 {} 的执行权限", userId, toolCode);
                throw new BusinessException(ErrorCode.FORBIDDEN, "无权限执行高危工具: " + toolCode);
            }
        }
    }

    /**
     * 判断用户是否为管理员（配置在 {@code mcp.admin.user-ids}）。
     */
    private boolean isAdmin(Long userId) {
        if (adminUserIds == null || adminUserIds.isBlank()) {
            return false;
        }
        return Arrays.stream(adminUserIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::valueOf)
                .anyMatch(id -> id.equals(userId));
    }
}
