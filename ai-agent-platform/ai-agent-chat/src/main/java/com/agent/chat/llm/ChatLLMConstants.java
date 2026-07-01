package com.agent.chat.llm;

public final class ChatLLMConstants {

    private ChatLLMConstants() {
    }

    public static final String TOOL_CALL_REGEX =
            "<tool_call>\\s*\\{\\s*\"name\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"arguments\"\\s*:\\s*(\\{[^}]*\\})\\s*\\}\\s*</tool_call>";
}
