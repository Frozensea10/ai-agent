package com.agent.core.llm.adapter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenAiAdapterTest {

    private final OpenAiAdapter adapter = new OpenAiAdapter("test-api-key");

    @Test
    @DisplayName("返回正确的 provider")
    void shouldReturnProvider() {
        assertEquals(LlmAdapterConstants.PROVIDER_OPENAI, adapter.getProvider());
    }

    @Test
    @DisplayName("空模型名默认回退到 gpt-4o-mini")
    void shouldResolveDefaultModelName() {
        String modelName = ReflectionTestUtils.invokeMethod(adapter, "resolveModelName", "");
        assertEquals("gpt-4o-mini", modelName);
    }

    @Test
    @DisplayName("正确解析 gpt-4o 模型名")
    void shouldResolveGpt4oModelName() {
        String modelName = ReflectionTestUtils.invokeMethod(adapter, "resolveModelName", "gpt-4o");
        assertEquals("gpt-4o", modelName);
    }

    @Test
    @DisplayName("未知模型名原样返回")
    void shouldReturnUnknownModelNameAsIs() {
        String modelName = ReflectionTestUtils.invokeMethod(adapter, "resolveModelName", "custom-model");
        assertEquals("custom-model", modelName);
    }
}
