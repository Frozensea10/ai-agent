package com.agent.knowledge.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 独立的异步文档处理器。
 * <p>
 * 从 {@link KnowledgeBaseService} 中拆分出来，确保 Spring AOP 代理能够正确拦截
 * 异步注解，避免同类自调用导致异步失效的问题。
 * <p>
 * 接收 {@code byte[]}、文件名与内容类型等基本类型作为参数，避免将
 * {@link MultipartFile} 跨越 HTTP 请求与异步线程的生命周期传递，防止临时文件
 * 在请求结束后被清理而导致异步线程读取失败。
 */
public interface DocumentAsyncProcessor {

    void processDocumentAsync(Long docId, Long kbId, byte[] fileContent, String originalFilename,
                              String contentType, String kbCode, String embeddingProvider,
                              String embeddingModel);
}
