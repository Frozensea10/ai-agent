package com.agent.core.controller;

import com.agent.common.result.Result;
import com.agent.core.dto.LlmProviderConfigDTO;
import com.agent.core.llm.service.LLMService;
import com.agent.core.service.LlmProviderConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "LLM 模型配置")
@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
public class LlmProviderConfigController {

    private final LlmProviderConfigService configService;
    private final LLMService llmService;

    @Operation(summary = "获取模型配置列表")
    @GetMapping("/model-providers")
    public Result<List<LlmProviderConfigDTO>> listConfigs() {
        return Result.success(configService.listConfigs());
    }

    @Operation(summary = "保存或更新模型配置")
    @PostMapping("/model-providers")
    public Result<Void> saveConfig(@Valid @RequestBody LlmProviderConfigDTO dto) {
        configService.saveConfig(dto);
        llmService.refreshProviderConfig(dto.getProviderName());
        return Result.success();
    }

    @Operation(summary = "删除模型配置")
    @DeleteMapping("/llm-providers/{id}")
    public Result<Void> deleteConfig(@PathVariable Long id) {
        configService.deleteConfig(id);
        return Result.success();
    }
}
