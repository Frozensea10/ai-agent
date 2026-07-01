package com.agent.chat.dto;

import lombok.Data;

@Data
public class SSEMessage {

    private String type;
    private String delta;
    private String messageId;
    private String finishReason;
    private Usage usage;
    private String toolName;
    private String arguments;
    private String result;

    @Data
    public static class Usage {
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
    }

    public static SSEMessage start(String messageId) {
        SSEMessage msg = new SSEMessage();
        msg.type = "start";
        msg.messageId = messageId;
        return msg;
    }

    public static SSEMessage content(String delta) {
        SSEMessage msg = new SSEMessage();
        msg.type = "content";
        msg.delta = delta;
        return msg;
    }

    public static SSEMessage toolCall(String toolName, String arguments) {
        SSEMessage msg = new SSEMessage();
        msg.type = "tool_call";
        msg.toolName = toolName;
        msg.arguments = arguments;
        return msg;
    }

    public static SSEMessage toolResult(String toolName, String result) {
        SSEMessage msg = new SSEMessage();
        msg.type = "tool_result";
        msg.toolName = toolName;
        msg.result = result;
        return msg;
    }

    public static SSEMessage end(String messageId, Usage usage) {
        SSEMessage msg = new SSEMessage();
        msg.type = "end";
        msg.messageId = messageId;
        msg.usage = usage;
        return msg;
    }

    public static SSEMessage error(String errorMessage) {
        SSEMessage msg = new SSEMessage();
        msg.type = "error";
        msg.delta = errorMessage;
        return msg;
    }
}
