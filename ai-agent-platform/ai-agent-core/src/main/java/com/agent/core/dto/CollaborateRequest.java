package com.agent.core.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 多 Agent 协作请求 DTO
 * 用于接收前端发起协作的请求参数
 */
@Data
public class CollaborateRequest {

    /**
     * 用户消息
     */
    @NotBlank(message = "用户消息不能为空")
    private String message;

    /**
     * 会话 ID，为空时由服务端生成
     */
    private String sessionId;
}
