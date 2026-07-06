package com.agent.chat.feign;

import com.agent.common.dto.AgentConfigDTO;
import com.agent.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class AgentFeignClientFallbackFactory implements FallbackFactory<AgentFeignClient> {

    @Override
    public AgentFeignClient create(Throwable cause) {
        log.error("Agent服务调用失败", cause);
        return new AgentFeignClient() {
            @Override
            public Result<List<AgentConfigDTO>> listAgents() {
                return Result.error("Agent服务不可用");
            }

            @Override
            public Result<AgentConfigDTO> getAgent(Long id, Long userId) {
                return Result.error("Agent服务不可用");
            }
        };
    }
}
