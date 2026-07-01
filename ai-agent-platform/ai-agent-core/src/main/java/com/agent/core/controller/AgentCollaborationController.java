package com.agent.core.controller;

import com.agent.common.result.Result;
import com.agent.core.collaboration.AgentCollaborationService;
import com.agent.core.collaboration.CollaborationResult;
import com.agent.core.entity.SubAgentExecution;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Agent协作控制器
 * 提供多Agent协作API
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/agents")
@RequiredArgsConstructor
public class AgentCollaborationController {

    private final AgentCollaborationService agentCollaborationService;

    /**
     * 执行多Agent协作
     */
    @PostMapping("/{agentId}/collaborate")
    public Result<CollaborationResult> collaborate(
            @PathVariable Long agentId,
            @RequestBody Map<String, String> request) {
        String message = request.get("message");
        String sessionId = request.getOrDefault("sessionId", "session_" + System.currentTimeMillis());

        log.info("多Agent协作请求，主Agent: {}, 会话: {}", agentId, sessionId);

        CollaborationResult result = agentCollaborationService.collaborate(agentId, message, sessionId);
        return Result.success(result);
    }

    /**
     * 获取子Agent执行历史
     */
    @GetMapping("/executions")
    public Result<List<SubAgentExecution>> getExecutionHistory(
            @RequestParam String sessionId) {
        List<SubAgentExecution> history = agentCollaborationService.getExecutionHistory(sessionId);
        return Result.success(history);
    }
}
