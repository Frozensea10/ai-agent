package com.agent.knowledge.chunk;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class DocumentChunker {

    private static final int DEFAULT_CHUNK_SIZE = 500;
    private static final int DEFAULT_OVERLAP = 50;

    public List<String> chunk(String content, Integer chunkSize, Integer overlap) {
        int size = chunkSize != null ? chunkSize : DEFAULT_CHUNK_SIZE;
        int ovl = overlap != null ? overlap : DEFAULT_OVERLAP;

        List<String> chunks = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return chunks;
        }

        String[] sentences = content.split("(?<=[。！？.!?])");
        StringBuilder currentChunk = new StringBuilder();

        for (String sentence : sentences) {
            sentence = sentence.trim();
            if (sentence.isEmpty()) continue;

            // If a single sentence exceeds chunk size, split it by characters
            if (sentence.length() > size) {
                if (currentChunk.length() > 0) {
                    chunks.add(currentChunk.toString().trim());
                    currentChunk = new StringBuilder();
                }
                for (int i = 0; i < sentence.length(); i += size - ovl) {
                    int end = Math.min(i + size, sentence.length());
                    chunks.add(sentence.substring(i, end));
                    if (end == sentence.length()) break;
                }
                continue;
            }

            if (currentChunk.length() + sentence.length() > size && currentChunk.length() > 0) {
                chunks.add(currentChunk.toString().trim());
                String previous = currentChunk.toString();
                currentChunk = new StringBuilder();
                if (previous.length() > ovl) {
                    currentChunk.append(previous.substring(previous.length() - ovl));
                }
            }
            currentChunk.append(sentence);
        }

        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }

        log.info("文档分块完成: {} chunks", chunks.size());
        return chunks;
    }
}
