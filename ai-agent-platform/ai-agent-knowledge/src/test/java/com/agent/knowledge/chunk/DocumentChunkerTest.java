package com.agent.knowledge.chunk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentChunkerTest {

    private final DocumentChunker chunker = new DocumentChunker();

    @Test
    @DisplayName("空内容返回空列表")
    void shouldReturnEmptyForBlankContent() {
        List<String> chunks = chunker.chunk("", 100, 10);
        assertTrue(chunks.isEmpty());
    }

    @Test
    @DisplayName("使用默认分块参数")
    void shouldUseDefaultChunkParameters() {
        List<String> chunks = chunker.chunk("这是第一句话。这是第二句话。", null, null);
        assertEquals(1, chunks.size());
    }

    @Test
    @DisplayName("按标点分句并分块")
    void shouldSplitBySentences() {
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            content.append("这是第").append(i).append("句测试内容。");
        }

        List<String> chunks = chunker.chunk(content.toString(), 50, 10);

        assertTrue(chunks.size() > 1);
        for (String chunk : chunks) {
            assertTrue(chunk.length() <= 50);
        }
    }

    @Test
    @DisplayName("单句超长时按字符滑动窗口处理")
    void shouldHandleLongSentence() {
        StringBuilder longSentence = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            longSentence.append("a");
        }
        longSentence.append("。");

        List<String> chunks = chunker.chunk(longSentence.toString(), 30, 5);

        assertTrue(chunks.size() > 1);
        for (String chunk : chunks) {
            assertTrue(chunk.length() <= 30);
        }
    }

    @Test
    @DisplayName("分块之间保留重叠内容")
    void shouldPreserveOverlapBetweenChunks() {
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            content.append("句子").append(i).append("的内容。");
        }

        List<String> chunks = chunker.chunk(content.toString(), 100, 20);

        if (chunks.size() > 1) {
            String firstChunk = chunks.get(0);
            String secondChunk = chunks.get(1);
            String overlap = firstChunk.substring(firstChunk.length() - 20);
            assertTrue(secondChunk.contains(overlap));
        }
    }
}
