package com.agent.core.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 子Agent执行记录表
 * 记录主Agent调用子Agent执行任务的完整生命周期
 */
@Data
@TableName("sub_agent_execution")
public class SubAgentExecution {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 主Agent ID
     */
    private Long masterAgentId;

    /**
     * 子Agent ID
     */
    private Long subAgentId;

    /**
     * 任务描述
     */
    private String taskDescription;

    /**
     * 任务状态: pending(待执行), running(执行中), success(成功), failed(失败)
     */
    private String taskStatus;

    /**
     * 任务执行结果
     */
    private String taskResult;

    /**
     * 执行耗时(ms)
     */
    private Integer executionTimeMs;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
