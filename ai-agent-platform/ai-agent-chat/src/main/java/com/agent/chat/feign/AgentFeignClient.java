package com.agent.chat.feign;

import com.agent.common.dto.AgentConfigDTO;
import com.agent.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

@FeignClient(name = "ai-agent-core", url = "${feign.client.url.core-service:http://localhost:8082}", path = "/api/v1/agents", fallbackFactory = AgentFeignClientFallbackFactory.class)
public interface AgentFeignClient {

    @GetMapping
    Result<List<AgentConfigDTO>> listAgents();

    @GetMapping("/{id}")
    Result<AgentConfigDTO> getAgent(@PathVariable("id") Long id, @RequestHeader("X-User-Id") Long userId);
}
