package com.agent.core.controller;

import com.agent.common.result.Result;
import com.agent.core.llm.service.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/embedding")
@RequiredArgsConstructor
public class EmbeddingController {

    private final EmbeddingService embeddingService;

    @PostMapping("/embed")
    public Result<List<Float>> embed(
            @RequestParam("provider") String provider,
            @RequestParam(value = "modelName", required = false) String modelName,
            @RequestParam("text") String text) {
        log.debug("Embedding 请求 - provider: {}, modelName: {}, text: {}", provider, modelName, text.length());
        List<Float> embedding = embeddingService.embed(provider, modelName, text);
        return Result.success(embedding);
    }

    @PostMapping("/embed-batch")
    public Result<List<List<Float>>> embedBatch(
            @RequestParam("provider") String provider,
            @RequestParam(value = "modelName", required = false) String modelName,
            @RequestBody List<String> texts) {
        log.debug("批量 Embedding 请求 - provider: {}, modelName: {}, 文本数: {}", provider, modelName, texts.size());
        List<List<Float>> embeddings = embeddingService.embedBatch(provider, modelName, texts);
        return Result.success(embeddings);
    }

    @GetMapping("/provider-available")
    public Result<Boolean> isProviderAvailable(@RequestParam("provider") String provider) {
        boolean available = embeddingService.isProviderAvailable(provider);
        return Result.success(available);
    }

    @PostMapping("/refresh-config")
    public Result<Void> refreshProviderConfig(@RequestParam("provider") String provider) {
        embeddingService.refreshProviderConfig(provider);
        return Result.success();
    }
}