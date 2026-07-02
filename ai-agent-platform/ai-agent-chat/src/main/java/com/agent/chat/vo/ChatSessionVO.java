package com.agent.chat.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatSessionVO {

    private Long id;
    private String sessionId;
    private Long agentId;
    private Long kbId;
    private String kbCode;
    private String sessionTitle;
    private Integer messageCount;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
