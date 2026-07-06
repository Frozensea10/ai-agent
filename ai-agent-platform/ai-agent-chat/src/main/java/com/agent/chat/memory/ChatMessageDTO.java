package com.agent.chat.memory;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDTO {

    private String role;
    private String content;

    public static ChatMessageDTO fromLangChainMessage(dev.langchain4j.data.message.ChatMessage message) {
        String role;
        String content;

        if (message instanceof UserMessage userMessage) {
            role = "user";
            content = userMessage.singleText();
        } else if (message instanceof AiMessage aiMessage) {
            role = "assistant";
            content = aiMessage.text();
        } else if (message instanceof SystemMessage systemMessage) {
            role = "system";
            content = systemMessage.text();
        } else if (message instanceof ToolExecutionResultMessage toolMessage) {
            role = "tool";
            content = toolMessage.text();
        } else {
            role = "user";
            content = message.toString();
        }

        return new ChatMessageDTO(role, content);
    }

    public dev.langchain4j.data.message.ChatMessage toLangChainMessage() {
        if (role == null) {
            return UserMessage.from(content);
        }
        return switch (role.toLowerCase()) {
            case "user" -> UserMessage.from(content);
            case "assistant", "ai" -> AiMessage.from(content);
            case "system" -> SystemMessage.from(content);
            case "tool" -> ToolExecutionResultMessage.builder().text(content).build();
            default -> UserMessage.from(content);
        };
    }
}