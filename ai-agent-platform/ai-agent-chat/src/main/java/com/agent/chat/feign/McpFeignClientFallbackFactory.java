package com.agent.chat.feign;

import com.agent.common.result.Result;
import com.agent.mcp.dto.ToolExecuteResult;
import com.agent.mcp.dto.ToolInfoDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class McpFeignClientFallbackFactory implements FallbackFactory<McpFeignClient> {

    @Override
    public McpFeignClient create(Throwable cause) {
        log.error("MCP服务调用失败", cause);
        return new McpFeignClient() {
            @Override
            public Result<List<ToolInfoDTO>> listTools() {
                return Result.error("MCP服务不可用");
            }

            @Override
            public Result<ToolExecuteResult> executeTool(String toolCode, Map<String, Object> parameters, Long userId) {
                return Result.error("MCP服务不可用");
            }
        };
    }
}
