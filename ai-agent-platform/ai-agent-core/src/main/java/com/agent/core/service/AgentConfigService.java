package com.agent.core.service;

import com.agent.core.dto.CreateAgentRequest;
import com.agent.core.dto.UpdateAgentRequest;
import com.agent.core.vo.AgentVO;

import java.util.List;

public interface AgentConfigService {

    AgentVO createAgent(CreateAgentRequest request, Long userId);

    AgentVO updateAgent(Long id, UpdateAgentRequest request, Long userId);

    void deleteAgent(Long id, Long userId);

    AgentVO getAgentById(Long id, Long userId);

    List<AgentVO> listAgents(Long userId);

    AgentVO getAgentByCode(String agentCode);
}
