package com.agent.core.controller;

import com.agent.common.result.Result;
import com.agent.core.llm.service.LLMService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * LLM 对话模型控制器
 * 提供对话模型的预热接口
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/llm")
@RequiredArgsConstructor
public class LLMController {

    private final LLMService llmService;

    /**
     * 预热对话模型：提前构建并缓存同步与流式模型实例
     *
     * @param request 模型预热请求参数
     * @return 空结果
     */
    @PostMapping("/warmup")
    public Result<Void> warmup(@Valid @RequestBody WarmupRequest request) {
        llmService.warmup(request.provider(), request.modelName(), request.temperature(), request.maxTokens());
        return Result.success();
    }

    /**
     * 模型预热请求
     *
     * @param provider    提供商名称（必填）
     * @param modelName   模型名称（可选）
     * @param temperature 温度参数（必填）
     * @param maxTokens   最大 Token 数（必填）
     */
    public record WarmupRequest(
            @NotBlank String provider,
            String modelName,
            @NotNull Double temperature,
            @NotNull Integer maxTokens
    ) {}
}
