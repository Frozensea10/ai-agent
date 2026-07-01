package com.agent.core.controller;

import com.agent.common.dto.AgentConfigDTO;
import com.agent.common.result.Result;
import com.agent.core.dto.CreateAgentRequest;
import com.agent.core.dto.UpdateAgentRequest;
import com.agent.core.service.AgentConfigService;
import com.agent.core.vo.AgentVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentConfigService agentConfigService;

    @GetMapping
    public Result<List<AgentConfigDTO>> listAgents(
            @RequestHeader("X-User-Id") Long userId) {
        List<AgentVO> agents = agentConfigService.listAgents(userId);
        return Result.success(agents.stream().map(this::convertToDTO).collect(Collectors.toList()));
    }

    @PostMapping
    public Result<AgentConfigDTO> createAgent(
            @Valid @RequestBody CreateAgentRequest request,
            @RequestHeader("X-User-Id") Long userId) {
        AgentVO vo = agentConfigService.createAgent(request, userId);
        return Result.success(convertToDTO(vo));
    }

    @GetMapping("/{id}")
    public Result<AgentConfigDTO> getAgent(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId) {
        AgentVO vo = agentConfigService.getAgentById(id, userId);
        return Result.success(convertToDTO(vo));
    }

    @PutMapping("/{id}")
    public Result<AgentConfigDTO> updateAgent(
            @PathVariable Long id,
            @RequestBody UpdateAgentRequest request,
            @RequestHeader("X-User-Id") Long userId) {
        AgentVO vo = agentConfigService.updateAgent(id, request, userId);
        return Result.success(convertToDTO(vo));
    }

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
