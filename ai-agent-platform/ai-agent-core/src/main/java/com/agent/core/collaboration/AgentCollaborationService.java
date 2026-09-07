package com.agent.core.collaboration;

import com.agent.core.entity.SubAgentExecution;

import java.util.List;

/**
 * Agent协作服务
 * 实现主从协作模式：主Agent分解任务，子Agent按依赖顺序并行执行，结果汇总
 */
public interface AgentCollaborationService {

    /**
     * 执行多Agent协作
     * 流程：校验主Agent → 任务分解 → 分配子Agent → 按依赖并行执行 → 汇总结果
     *
     * @param masterAgentId 主Agent ID
     * @param userRequest   用户请求
     * @param sessionId     会话ID，为空或空白时自动生成
     * @return 协作执行结果
     */
    CollaborationResult collaborate(Long masterAgentId, String userRequest, String sessionId);

    /**
     * 获取子Agent执行历史
     */
    List<SubAgentExecution> getExecutionHistory(String sessionId);
}
