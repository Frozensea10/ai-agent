package com.agent.knowledge.parser;

import com.agent.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentParserTest {

    private final DocumentParser parser = new DocumentParser();

    @Test
    @DisplayName("解析 UTF-8 文本文件成功")
    void shouldParseUtf8TextFile() {
        String content = "这是一个测试文本。";
        MultipartFile file = new MockMultipartFile("test.txt", "test.txt", "text/plain", content.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        String result = parser.parse(file);

        assertEquals(content + "\n", result);
    }

    @Test
    @DisplayName("解析 Markdown 文件成功")
    void shouldParseMarkdownFile() {
        String content = "# 标题\n\n正文内容。";
        MultipartFile file = new MockMultipartFile("test.md", "test.md", "text/markdown", content.getBytes());

        String result = parser.parse(file);

        assertEquals(content + "\n", result);
    }

    @Test
    @DisplayName("文件大小超过 50MB 抛出异常")
    void shouldThrowWhenFileTooLarge() {
        byte[] largeContent = new byte[50 * 1024 * 1024 + 1];
        MultipartFile file = new MockMultipartFile("large.txt", "large.txt", "text/plain", largeContent);

        BusinessException exception = assertThrows(BusinessException.class, () -> parser.parse(file));
        assertTrue(exception.getMessage().contains("文件大小超过限制"));
    }

    @Test
    @DisplayName("文件名为空抛出异常")
    void shouldThrowWhenFilenameEmpty() {
        MultipartFile file = new MockMultipartFile("test", "", "text/plain", "content".getBytes());

        BusinessException exception = assertThrows(BusinessException.class, () -> parser.parse(file));
        assertTrue(exception.getMessage().contains("文件名不能为空"));
    }

    @Test
    @DisplayName("缺少扩展名抛出异常")
    void shouldThrowWhenMissingExtension() {
        MultipartFile file = new MockMultipartFile("test", "test", "text/plain", "content".getBytes());

        BusinessException exception = assertThrows(BusinessException.class, () -> parser.parse(file));
        assertTrue(exception.getMessage().contains("文件名缺少扩展名"));
    }

    @Test
    @DisplayName("不支持的文件类型抛出异常")
    void shouldThrowWhenUnsupportedFileType() {
        MultipartFile file = new MockMultipartFile("test.xls", "test.xls", "application/vnd.ms-excel", "content".getBytes());

        BusinessException exception = assertThrows(BusinessException.class, () -> parser.parse(file));
        assertTrue(exception.getMessage().contains("不支持的文件类型"));
    }

    @Test
    @DisplayName("解析 UTF-8 BOM 文本文件成功")
    void shouldParseUtf8BomTextFile() {
        String content = "BOM 测试内容。";
        byte[] bytes = content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] bomBytes = new byte[bytes.length + 3];
        bomBytes[0] = (byte) 0xEF;
        bomBytes[1] = (byte) 0xBB;
        bomBytes[2] = (byte) 0xBF;
        System.arraycopy(bytes, 0, bomBytes, 3, bytes.length);

        MultipartFile file = new MockMultipartFile("test.txt", "test.txt", "text/plain", bomBytes);

        String result = parser.parse(file);

        assertEquals(content + "\n", result);
    }
}
