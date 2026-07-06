package com.agent.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SendMessageRequest {

    @NotBlank(message = "消息内容不能为空")
    private String content;

    @NotNull(message = "Agent ID 不能为空")
    private Long agentId;

    private Long kbId;

    private String kbCode;

    private String sessionId;

    private String modelProvider;

    private String modelName;
}
