package com.agent.core.controller;

import com.agent.common.dto.AgentConfigDTO;
import com.agent.common.result.Result;
import com.agent.core.dto.CreateAgentRequest;
import com.agent.core.dto.UpdateAgentRequest;
import com.agent.core.service.AgentConfigService;
import com.agent.core.vo.AgentVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Tag(name = "Agent 管理", description = "Agent 配置与生命周期管理接口")
@RestController
@RequestMapping("/api/v1/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentConfigService agentConfigService;

    @Operation(summary = "查询 Agent 列表", description = "根据当前用户查询其拥有的所有 Agent 配置")
    @GetMapping
    public Result<List<AgentConfigDTO>> listAgents(
            @RequestHeader("X-User-Id") Long userId) {
        List<AgentVO> agents = agentConfigService.listAgents(userId);
        return Result.success(agents.stream().map(this::convertToDTO).collect(Collectors.toList()));
    }

    @Operation(summary = "创建 Agent", description = "为当前用户创建新的 Agent 配置")
    @PostMapping
    public Result<AgentConfigDTO> createAgent(
            @Valid @RequestBody CreateAgentRequest request,
            @RequestHeader("X-User-Id") Long userId) {
        AgentVO vo = agentConfigService.createAgent(request, userId);
        return Result.success(convertToDTO(vo));
    }

    @Operation(summary = "查询 Agent 详情", description = "根据 Agent ID 查询指定 Agent 配置")
    @GetMapping("/{id}")
    public Result<AgentConfigDTO> getAgent(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId) {
        AgentVO vo = agentConfigService.getAgentById(id, userId);
        return Result.success(convertToDTO(vo));
    }

    @Operation(summary = "更新 Agent", description = "根据 Agent ID 更新 Agent 配置")
    @PutMapping("/{id}")
    public Result<AgentConfigDTO> updateAgent(
            @PathVariable Long id,
            @RequestBody UpdateAgentRequest request,
            @RequestHeader("X-User-Id") Long userId) {
        AgentVO vo = agentConfigService.updateAgent(id, request, userId);
        return Result.success(convertToDTO(vo));
    }

    @Operation(summary = "删除 Agent", description = "根据 Agent ID 删除指定 Agent 配置")
    @DeleteMapping("/{id}")
    public Result<Void> deleteAgent(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId) {
        agentConfigService.deleteAgent(id, userId);
        return Result.success();
    }

    private AgentConfigDTO convertToDTO(AgentVO vo) {
        AgentConfigDTO dto = new AgentConfigDTO();
        dto.setId(vo.getId());
        dto.setAgentName(vo.getAgentName());
        dto.setAgentCode(vo.getAgentCode());
        dto.setDescription(vo.getDescription());
        dto.setModelProvider(vo.getModelProvider());
        dto.setModelName(vo.getModelName());
        dto.setSystemPrompt(vo.getSystemPrompt());
        dto.setTemperature(vo.getTemperature());
        dto.setMaxTokens(vo.getMaxTokens());
        dto.setMemoryType(vo.getMemoryType());
        dto.setMemoryMaxMessages(vo.getMemoryMaxMessages());
        dto.setStatus(vo.getStatus());
        dto.setCreatedAt(vo.getCreatedAt());
        dto.setUpdatedAt(vo.getUpdatedAt());
        return dto;
    }
}
