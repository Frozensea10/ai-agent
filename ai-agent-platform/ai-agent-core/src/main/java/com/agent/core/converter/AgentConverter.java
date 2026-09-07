package com.agent.core.converter;

import com.agent.common.dto.AgentConfigDTO;
import com.agent.core.vo.AgentVO;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Agent 对象转换器
 * 负责 AgentVO 与 AgentConfigDTO 之间的转换
 */
public final class AgentConverter {

    private AgentConverter() {
    }

    /**
     * 将 AgentVO 转换为对外返回的 AgentConfigDTO
     *
     * @param vo Agent 视图对象
     * @return Agent 配置 DTO，vo 为 null 时返回 null
     */
    public static AgentConfigDTO toDTO(AgentVO vo) {
        if (vo == null) {
            return null;
        }
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

    /**
     * 将 AgentVO 列表批量转换为 AgentConfigDTO 列表
     *
     * @param vos Agent 视图对象列表
     * @return Agent 配置 DTO 列表
     */
    public static List<AgentConfigDTO> toDTOList(List<AgentVO> vos) {
        return vos.stream().map(AgentConverter::toDTO).collect(Collectors.toList());
    }
}
