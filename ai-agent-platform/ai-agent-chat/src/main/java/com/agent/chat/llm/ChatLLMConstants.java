package com.agent.chat.llm;

public final class ChatLLMConstants {

    private ChatLLMConstants() {
    }

    public static final String TOOL_CALL_REGEX =
            "<tool_call>\\s*\\{\\s*\"name\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"arguments\"\\s*:\\s*(\\{[\\s\\S]*?\\})\\s*\\}\\s*</tool_call>";

    public static final String JSON_CODE_BLOCK_REGEX =
            "```(?:json)?\\s*\\{\\s*\"name\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"arguments\"\\s*:\\s*(\\{[\\s\\S]*?\\})\\s*\\}\\s*```";

    public static final String FUNCTION_CALL_REGEX =
            "\\{\\s*\"function_call\"\\s*:\\s*\\{\\s*\"name\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"arguments\"\\s*:\\s*\"([^\"]*)\"\\s*\\}\\s*\\}";

    public static final String TOOL_NAME_REGEX =
            "\\{\\s*\"tool_name\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"arguments\"\\s*:\\s*(\\{[\\s\\S]*?\\})\\s*\\}";

    public static final String PLAIN_TOOL_CALL_REGEX =
            "\\{\\s*\"name\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"arguments\"\\s*:\\s*(\\{[\\s\\S]*?\\})\\s*\\}";
}
