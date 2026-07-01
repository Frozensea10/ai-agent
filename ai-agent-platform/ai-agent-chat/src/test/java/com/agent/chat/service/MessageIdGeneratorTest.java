package com.agent.chat.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageIdGeneratorTest {

    @Test
    @DisplayName("生成的消息 ID 以 msg_ 开头")
    void shouldGenerateMessageIdWithPrefix() {
        String messageId = MessageIdGenerator.generate();
        assertTrue(messageId.startsWith("msg_"));
    }

    @Test
    @DisplayName("生成的消息 ID 长度符合预期")
    void shouldGenerateMessageIdWithCorrectLength() {
        String messageId = MessageIdGenerator.generate();
        assertEquals(20, messageId.length());
    }

    @Test
    @DisplayName("多次生成不重复")
    void shouldGenerateUniqueMessageIds() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            ids.add(MessageIdGenerator.generate());
        }
        assertEquals(100, ids.size());
    }
}
