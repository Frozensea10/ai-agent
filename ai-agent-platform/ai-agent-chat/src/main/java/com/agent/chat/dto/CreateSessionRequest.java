package com.agent.chat.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateSessionRequest {

    @NotNull(message = "Agent ID 不能为空")
    private Long agentId;

    private Long kbId;

    private String title;
}
