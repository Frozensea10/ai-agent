package com.agent.core.service;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.core.dto.CreateAgentRequest;
import com.agent.core.dto.UpdateAgentRequest;
import com.agent.core.entity.AgentConfig;
import com.agent.core.mapper.AgentConfigMapper;
import com.agent.core.vo.AgentVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentConfigService {

    private static final String CACHE_NAME = "agentConfig";
    private static final String CACHE_KEY_LIST = "'list'";
    private static final String CACHE_KEY_PREFIX = "'agent:'";

    private static final String DEFAULT_MEMORY_TYPE = "window";
    private static final int DEFAULT_MEMORY_MAX_MESSAGES = 10;
    private static final String DEFAULT_AGENT_TYPE = "single";
    private static final int DEFAULT_PRIORITY = 0;
    private static final int ACTIVE_STATUS = 1;

    private final AgentConfigMapper agentConfigMapper;

    @CacheEvict(value = CACHE_NAME, key = CACHE_KEY_PREFIX + " + #result.agentCode")
    public AgentVO createAgent(CreateAgentRequest request, Long userId) {
        AgentConfig existing = agentConfigMapper.selectByCode(request.getAgentCode());
        if (existing != null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "Agent 编码已存在");
        }

        AgentConfig config = new AgentConfig();
        config.setAgentName(request.getAgentName());
        config.setAgentCode(request.getAgentCode());
        config.setDescription(request.getDescription());
        config.setModelProvider(request.getModelProvider());
        config.setModelName(request.getModelName());
        config.setSystemPrompt(request.getSystemPrompt());
        config.setTemperature(request.getTemperature());
        config.setMaxTokens(request.getMaxTokens());
        config.setMemoryType(request.getMemoryType() != null ? request.getMemoryType() : DEFAULT_MEMORY_TYPE);
        config.setMemoryMaxMessages(request.getMemoryMaxMessages() != null ? request.getMemoryMaxMessages() : DEFAULT_MEMORY_MAX_MESSAGES);
        config.setAgentType(request.getAgentType() != null ? request.getAgentType() : DEFAULT_AGENT_TYPE);
        config.setParentAgentId(request.getParentAgentId());
        config.setCapabilities(request.getCapabilities());
        config.setPriority(request.getPriority() != null ? request.getPriority() : DEFAULT_PRIORITY);
        config.setStatus(ACTIVE_STATUS);
        config.setCreatedBy(userId);

        agentConfigMapper.insert(config);
        return convertToVO(config);
    }

    @CacheEvict(value = CACHE_NAME, key = CACHE_KEY_PREFIX + " + #result.agentCode")
    public AgentVO updateAgent(Long id, UpdateAgentRequest request) {
        AgentConfig config = agentConfigMapper.selectById(id);
        if (config == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "Agent 不存在");
        }

        if (request.getAgentName() != null) config.setAgentName(request.getAgentName());
        if (request.getDescription() != null) config.setDescription(request.getDescription());
        if (request.getModelProvider() != null) config.setModelProvider(request.getModelProvider());
        if (request.getModelName() != null) config.setModelName(request.getModelName());
        if (request.getSystemPrompt() != null) config.setSystemPrompt(request.getSystemPrompt());
        if (request.getTemperature() != null) config.setTemperature(request.getTemperature());
        if (request.getMaxTokens() != null) config.setMaxTokens(request.getMaxTokens());
        if (request.getMemoryType() != null) config.setMemoryType(request.getMemoryType());
        if (request.getMemoryMaxMessages() != null) config.setMemoryMaxMessages(request.getMemoryMaxMessages());
        if (request.getAgentType() != null) config.setAgentType(request.getAgentType());
        if (request.getParentAgentId() != null) config.setParentAgentId(request.getParentAgentId());
        if (request.getCapabilities() != null) config.setCapabilities(request.getCapabilities());
        if (request.getPriority() != null) config.setPriority(request.getPriority());
        if (request.getStatus() != null) config.setStatus(request.getStatus());

        agentConfigMapper.updateById(config);
        return convertToVO(config);
    }

    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public void deleteAgent(Long id) {
        AgentConfig config = agentConfigMapper.selectById(id);
        if (config == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "Agent 不存在");
        }
        agentConfigMapper.deleteById(id);
    }

    @Cacheable(value = CACHE_NAME, key = CACHE_KEY_PREFIX + " + #id")
    public AgentVO getAgentById(Long id) {
        AgentConfig config = agentConfigMapper.selectById(id);
        if (config == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "Agent 不存在");
        }
        return convertToVO(config);
    }

    @Cacheable(value = CACHE_NAME, key = CACHE_KEY_LIST)
    public List<AgentVO> listAgents() {
        return agentConfigMapper.selectAllActive().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Cacheable(value = CACHE_NAME, key = CACHE_KEY_PREFIX + " + #agentCode")
    public AgentVO getAgentByCode(String agentCode) {
        AgentConfig config = agentConfigMapper.selectByCode(agentCode);
        if (config == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "Agent 不存在");
        }
        return convertToVO(config);
    }

    private AgentVO convertToVO(AgentConfig config) {
        AgentVO vo = new AgentVO();
        vo.setId(config.getId());
        vo.setAgentName(config.getAgentName());
        vo.setAgentCode(config.getAgentCode());
        vo.setDescription(config.getDescription());
        vo.setModelProvider(config.getModelProvider());
        vo.setModelName(config.getModelName());
        vo.setSystemPrompt(config.getSystemPrompt());
        vo.setTemperature(config.getTemperature());
        vo.setMaxTokens(config.getMaxTokens());
        vo.setMemoryType(config.getMemoryType());
        vo.setMemoryMaxMessages(config.getMemoryMaxMessages());
        vo.setAgentType(config.getAgentType());
        vo.setParentAgentId(config.getParentAgentId());
        vo.setCapabilities(config.getCapabilities());
        vo.setPriority(config.getPriority());
        vo.setStatus(config.getStatus());
        vo.setCreatedAt(config.getCreatedAt());
        vo.setUpdatedAt(config.getUpdatedAt());
        return vo;
    }
}
