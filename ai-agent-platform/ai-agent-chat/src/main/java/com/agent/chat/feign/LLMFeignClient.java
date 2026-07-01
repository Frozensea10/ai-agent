package com.agent.chat.feign;

import com.agent.common.result.Result;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "ai-agent-core", path = "/api/v1/llm")
public interface LLMFeignClient {

    @GetMapping("/chat-model")
    Result<Void> createChatModel(
            @RequestParam("provider") String provider,
            @RequestParam("modelName") String modelName,
            @RequestParam("temperature") Double temperature,
            @RequestParam("maxTokens") Integer maxTokens);

    @GetMapping("/streaming-model")
    Result<Void> createStreamingModel(
            @RequestParam("provider") String provider,
            @RequestParam("modelName") String modelName,
            @RequestParam("temperature") Double temperature,
            @RequestParam("maxTokens") Integer maxTokens);
}
