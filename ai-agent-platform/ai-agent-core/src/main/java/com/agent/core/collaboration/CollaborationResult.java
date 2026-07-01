package com.agent.core.collaboration;

import lombok.Data;

import java.util.List;

/**
 * 多Agent协作结果
 */
@Data
public class CollaborationResult {

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 主Agent ID
     */
    private Long masterAgentId;

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 最终回答
     */
    private String finalAnswer;

    /**
     * 子任务列表
     */
    private List<SubTask> subTasks;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 执行总耗时(ms)
     */
    private Long executionTimeMs;
}
