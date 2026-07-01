package com.agent.core.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_config")
public class AgentConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String agentName;

    private String agentCode;

    private String description;

    private String modelProvider;

    private String modelName;

    private String systemPrompt;

    private Double temperature;

    private Integer maxTokens;

    private String memoryType;

    private Integer memoryMaxMessages;

    /**
     * Agent类型: single(单Agent), master(主Agent), sub(子Agent)
     */
    private String agentType;

    /**
     * 父Agent ID，子Agent关联的主Agent
     */
    private Long parentAgentId;

    /**
     * 能力标签JSON，如 ["code-execution", "data-analysis", "web-search"]
     */
    private String capabilities;

    /**
     * 执行优先级，数值越大优先级越高
     */
    private Integer priority;

    private Integer status;

    private Long createdBy;

    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
