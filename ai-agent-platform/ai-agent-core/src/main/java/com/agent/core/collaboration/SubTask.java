package com.agent.core.collaboration;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 子任务定义
 */
@Data
public class SubTask {

    /**
     * 任务唯一ID
     */
    private String taskId;

    /**
     * 任务名称
     */
    private String taskName;

    /**
     * 任务详细描述
     */
    private String taskDescription;

    /**
     * 所需能力标签
     */
    private String requiredCapability;

    /**
     * 依赖的其他任务ID列表
     */
    private List<String> dependsOn;

    /**
     * 依赖此任务的其他任务（运行时填充）
     */
    private List<SubTask> dependents = new ArrayList<>();

    /**
     * 任务执行结果（运行时填充）
     */
    private String result;

    /**
     * 任务状态（运行时填充）
     */
    private String status = "pending";

    /**
     * 执行耗时ms（运行时填充）
     */
    private Long executionTimeMs;

    /**
     * 分配的Agent ID（运行时填充）
     */
    private Long assignedAgentId;

    public void addDependent(SubTask task) {
        this.dependents.add(task);
    }

    /**
     * 检查任务是否已完成
     */
    public boolean isCompleted() {
        return "success".equals(status) || "failed".equals(status);
    }

    /**
     * 检查任务是否可以执行（所有依赖已完成）
     */
    public boolean isReady() {
        if (dependsOn == null || dependsOn.isEmpty()) {
            return true;
        }
        // 依赖检查由外部调度器处理
        return "pending".equals(status);
    }
}
