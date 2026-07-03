package com.agent.core.controller;

import com.agent.common.result.Result;
import com.agent.core.dto.LlmProviderConfigDTO;
import com.agent.core.service.LlmProviderConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "LLM 模型配置")
@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
public class LlmProviderConfigController {

    private final LlmProviderConfigService configService;

    @Operation(summary = "获取模型配置列表")
    @GetMapping("/model-providers")
    public Result<List<LlmProviderConfigDTO>> listConfigs() {
        return Result.success(configService.listConfigs());
    }

    @Operation(summary = "保存或更新模型配置")
    @PostMapping("/model-providers")
    public Result<Void> saveConfig(@RequestBody LlmProviderConfigDTO dto) {
        configService.saveConfig(dto);
        return Result.success();
    }
}
