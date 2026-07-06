package com.agent.chat.memory;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatMessageDTOTest {

    @Test
    @DisplayName("fromLangChainMessage: UserMessage 映射为 role=user")
    void fromLangChainMessage_userMessage() {
        ChatMessageDTO dto = ChatMessageDTO.fromLangChainMessage(UserMessage.from("你好"));

        assertThat(dto.getRole()).isEqualTo("user");
        assertThat(dto.getContent()).isEqualTo("你好");
    }

    @Test
    @DisplayName("fromLangChainMessage: AiMessage 映射为 role=assistant")
    void fromLangChainMessage_aiMessage() {
        ChatMessageDTO dto = ChatMessageDTO.fromLangChainMessage(AiMessage.from("AI 回复"));

        assertThat(dto.getRole()).isEqualTo("assistant");
        assertThat(dto.getContent()).isEqualTo("AI 回复");
    }

    @Test
    @DisplayName("fromLangChainMessage: SystemMessage 映射为 role=system")
    void fromLangChainMessage_systemMessage() {
        ChatMessageDTO dto = ChatMessageDTO.fromLangChainMessage(SystemMessage.from("系统提示"));

        assertThat(dto.getRole()).isEqualTo("system");
        assertThat(dto.getContent()).isEqualTo("系统提示");
    }

    @Test
    @DisplayName("fromLangChainMessage: ToolExecutionResultMessage 映射为 role=tool（覆盖新增分支）")
    void fromLangChainMessage_toolExecutionResultMessage() {
        ToolExecutionResultMessage msg = ToolExecutionResultMessage.builder().text("工具结果").build();

        ChatMessageDTO dto = ChatMessageDTO.fromLangChainMessage(msg);

        assertThat(dto.getRole()).isEqualTo("tool");
        assertThat(dto.getContent()).isEqualTo("工具结果");
    }

    @Test
    @DisplayName("toLangChainMessage: role=user 还原为 UserMessage")
    void toLangChainMessage_userRole() {
        ChatMessageDTO dto = new ChatMessageDTO("user", "hi");

        ChatMessage msg = dto.toLangChainMessage();

        assertThat(msg).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) msg).singleText()).isEqualTo("hi");
    }

    @Test
    @DisplayName("toLangChainMessage: role=assistant/ai 还原为 AiMessage")
    void toLangChainMessage_assistantRole() {
        ChatMessageDTO dto = new ChatMessageDTO("assistant", "hello");

        ChatMessage msg = dto.toLangChainMessage();

        assertThat(msg).isInstanceOf(AiMessage.class);
        assertThat(((AiMessage) msg).text()).isEqualTo("hello");
    }

    @Test
    @DisplayName("toLangChainMessage: role=ai 也可还原为 AiMessage")
    void toLangChainMessage_aiRole() {
        ChatMessageDTO dto = new ChatMessageDTO("ai", "hello-ai");

        ChatMessage msg = dto.toLangChainMessage();

        assertThat(msg).isInstanceOf(AiMessage.class);
        assertThat(((AiMessage) msg).text()).isEqualTo("hello-ai");
    }

    @Test
    @DisplayName("toLangChainMessage: role=system 还原为 SystemMessage")
    void toLangChainMessage_systemRole() {
        ChatMessageDTO dto = new ChatMessageDTO("system", "prompt");

        ChatMessage msg = dto.toLangChainMessage();

        assertThat(msg).isInstanceOf(SystemMessage.class);
        assertThat(((SystemMessage) msg).text()).isEqualTo("prompt");
    }

    @Test
    @DisplayName("toLangChainMessage: role=tool 还原为 ToolExecutionResultMessage（覆盖新增分支）")
    void toLangChainMessage_toolRole() {
        ChatMessageDTO dto = new ChatMessageDTO("tool", "tool-result");

        ChatMessage msg = dto.toLangChainMessage();

        assertThat(msg).isInstanceOf(ToolExecutionResultMessage.class);
        assertThat(((ToolExecutionResultMessage) msg).text()).isEqualTo("tool-result");
    }

    @Test
    @DisplayName("toLangChainMessage: role=null 默认还原为 UserMessage")
    void toLangChainMessage_nullRole() {
        ChatMessageDTO dto = new ChatMessageDTO(null, "fallback");

        ChatMessage msg = dto.toLangChainMessage();

        assertThat(msg).isInstanceOf(UserMessage.class);
    }

    @Test
    @DisplayName("toLangChainMessage: 未知 role 默认还原为 UserMessage")
    void toLangChainMessage_unknownRole() {
        ChatMessageDTO dto = new ChatMessageDTO("unknown-role", "x");

        ChatMessage msg = dto.toLangChainMessage();

        assertThat(msg).isInstanceOf(UserMessage.class);
    }

    @Test
    @DisplayName("role 大小写不敏感：USER / User / user 均映射为 UserMessage")
    void toLangChainMessage_roleCaseInsensitive() {
        assertThat(new ChatMessageDTO("USER", "a").toLangChainMessage()).isInstanceOf(UserMessage.class);
        assertThat(new ChatMessageDTO("User", "a").toLangChainMessage()).isInstanceOf(UserMessage.class);
        assertThat(new ChatMessageDTO("ASSISTANT", "a").toLangChainMessage()).isInstanceOf(AiMessage.class);
        assertThat(new ChatMessageDTO("SYSTEM", "a").toLangChainMessage()).isInstanceOf(SystemMessage.class);
        assertThat(new ChatMessageDTO("TOOL", "a").toLangChainMessage()).isInstanceOf(ToolExecutionResultMessage.class);
    }

    @Test
    @DisplayName("双向转换：UserMessage -> DTO -> UserMessage 内容保持一致")
    void roundTrip_userMessage() {
        ChatMessageDTO dto = ChatMessageDTO.fromLangChainMessage(UserMessage.from("round-trip"));

        ChatMessage restored = dto.toLangChainMessage();

        assertThat(restored).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) restored).singleText()).isEqualTo("round-trip");
    }
}
