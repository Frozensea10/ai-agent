package com.agent.knowledge.parser;

import com.agent.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class DocumentParser {

    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB
    private static final int UTF8_BOM_LENGTH = 3;
    private static final byte UTF8_BOM_0 = (byte) 0xEF;
    private static final byte UTF8_BOM_1 = (byte) 0xBB;
    private static final byte UTF8_BOM_2 = (byte) 0xBF;

    public String parse(MultipartFile file) {
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("文件大小超过限制 (最大 50MB)");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            throw new BusinessException("文件名不能为空");
        }

        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex < 0 || lastDotIndex == filename.length() - 1) {
            throw new BusinessException("文件名缺少扩展名");
        }
        String ext = filename.substring(lastDotIndex + 1).toLowerCase();
        return switch (ext) {
            case "pdf" -> parsePdf(file);
            case "docx" -> parseDocx(file);
            case "doc" -> parseDoc(file);
            case "txt", "md", "markdown" -> parseText(file);
            default -> throw new BusinessException("不支持的文件类型: " + ext);
        };
    }

    private String parsePdf(MultipartFile file) {
        try (PDDocument document = Loader.loadPDF(file.getInputStream().readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        } catch (IOException e) {
            log.error("PDF 解析失败", e);
            throw new BusinessException("PDF 解析失败: " + e.getMessage());
        }
    }

    private String parseDocx(MultipartFile file) {
        try (XWPFDocument document = new XWPFDocument(file.getInputStream());
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        } catch (IOException e) {
            log.error("Word (.docx) 解析失败", e);
            throw new BusinessException("Word 解析失败: " + e.getMessage());
        }
    }

    private String parseDoc(MultipartFile file) {
        try (HWPFDocument document = new HWPFDocument(file.getInputStream());
             WordExtractor extractor = new WordExtractor(document)) {
            return extractor.getText();
        } catch (IOException e) {
            log.error("Word (.doc) 解析失败", e);
            throw new BusinessException("Word 解析失败: " + e.getMessage());
        }
    }

    private String parseText(MultipartFile file) {
        EncodingResult encodingResult = detectEncoding(file);
        Charset charset = encodingResult.charset;
        int bomLength = encodingResult.bomLength;
        try (InputStream inputStream = file.getInputStream()) {
            if (bomLength > 0) {
                inputStream.skip(bomLength);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, charset))) {
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
                return content.toString();
            }
        } catch (IOException e) {
            log.error("文本解析失败", e);
            throw new BusinessException("文本解析失败: " + e.getMessage());
        }
    }

    private EncodingResult detectEncoding(MultipartFile file) {
        byte[] bom = new byte[4];
        int read;
        try (InputStream inputStream = file.getInputStream()) {
            read = inputStream.read(bom);
        } catch (IOException e) {
            return new EncodingResult(StandardCharsets.UTF_8, 0);
        }
        if (read < 0) {
            return new EncodingResult(StandardCharsets.UTF_8, 0);
        }
        if (read >= UTF8_BOM_LENGTH && bom[0] == UTF8_BOM_0 && bom[1] == UTF8_BOM_1 && bom[2] == UTF8_BOM_2) {
            return new EncodingResult(StandardCharsets.UTF_8, UTF8_BOM_LENGTH);
        }
        if (read >= 2 && bom[0] == (byte) 0xFE && bom[1] == (byte) 0xFF) {
            return new EncodingResult(StandardCharsets.UTF_16BE, 2);
        }
        if (read >= 2 && bom[0] == (byte) 0xFF && bom[1] == (byte) 0xFE) {
            return new EncodingResult(StandardCharsets.UTF_16LE, 2);
        }
        return new EncodingResult(StandardCharsets.UTF_8, 0);
    }

    private static class EncodingResult {
        final Charset charset;
        final int bomLength;

        EncodingResult(Charset charset, int bomLength) {
            this.charset = charset;
            this.bomLength = bomLength;
        }
    }
}
