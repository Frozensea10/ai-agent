package com.agent.knowledge.feign;

import com.agent.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "ai-agent-core", url = "http://localhost:8082", path = "/api/v1/embedding")
public interface EmbeddingFeignClient {

    @PostMapping("/embed")
    Result<List<Float>> embed(
            @RequestParam("provider") String provider,
            @RequestParam(value = "modelName", required = false) String modelName,
            @RequestParam("text") String text);

    @PostMapping("/embed-batch")
    Result<List<List<Float>>> embedBatch(
            @RequestParam("provider") String provider,
            @RequestParam(value = "modelName", required = false) String modelName,
            @RequestBody List<String> texts);

    @GetMapping("/provider-available")
    Result<Boolean> isProviderAvailable(@RequestParam("provider") String provider);
}