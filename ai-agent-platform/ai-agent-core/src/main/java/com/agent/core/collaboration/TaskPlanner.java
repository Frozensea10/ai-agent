package com.agent.core.collaboration;

import com.agent.core.entity.AgentConfig;

import java.util.List;

/**
 * 任务规划器
 * 使用LLM将用户请求分解为可执行的子任务列表
 */
public interface TaskPlanner {

    /**
     * 分解任务
     *
     * @param userRequest 用户请求
     * @param masterAgent 主Agent配置
     * @return 子任务列表
     */
    List<SubTask> decomposeTasks(String userRequest, AgentConfig masterAgent);
}
