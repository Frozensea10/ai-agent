package com.agent.chat.feign;

import com.agent.common.dto.AgentConfigDTO;
import com.agent.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(name = "ai-agent-core", path = "/api/v1/agents")
public interface AgentFeignClient {

    @GetMapping
    Result<List<AgentConfigDTO>> listAgents();

    @GetMapping("/{id}")
    Result<AgentConfigDTO> getAgent(@PathVariable("id") Long id);
}
