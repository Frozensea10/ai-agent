package com.agent.core.controller;

import com.agent.common.result.Result;
import com.agent.core.llm.service.LLMService;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Slf4j
@RestController
@RequestMapping("/api/v1/llm")
@RequiredArgsConstructor
public class LLMController {

    private final LLMService llmService;

    @PostMapping("/chat-model")
    public Result<Void> buildChatModel(@Valid @RequestBody BuildModelRequest request) {
        ChatModel model = llmService.createChatModel(request.provider, request.modelName, request.temperature, request.maxTokens);
        log.info("Created chat model for provider: {}, model: {}", request.provider, request.modelName);
        return Result.success();
    }

    @PostMapping("/streaming-model")
    public Result<Void> buildStreamingModel(@Valid @RequestBody BuildModelRequest request) {
        StreamingChatModel model = llmService.createStreamingModel(request.provider, request.modelName, request.temperature, request.maxTokens);
        log.info("Created streaming model for provider: {}, model: {}", request.provider, request.modelName);
        return Result.success();
    }

    public record BuildModelRequest(
            @NotBlank String provider,
            String modelName,
            @NotNull Double temperature,
            @NotNull Integer maxTokens
    ) {}
}
