package com.agent.chat.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatMessageVO {

    private Long id;
    private String messageId;
    private String role;
    private String content;
    private String contentType;
    private Integer tokensUsed;
    private String modelName;
    private LocalDateTime createdAt;
}
