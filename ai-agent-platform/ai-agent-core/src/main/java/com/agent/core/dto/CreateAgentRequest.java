package com.agent.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateAgentRequest {

    @NotBlank(message = "Agent 名称不能为空")
    private String agentName;

    @NotBlank(message = "Agent 编码不能为空")
    private String agentCode;

    private String description;

    @NotBlank(message = "模型提供商不能为空")
    private String modelProvider;

    private String modelName;

    private String systemPrompt;

    @NotNull(message = "temperature 不能为空")
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
}
