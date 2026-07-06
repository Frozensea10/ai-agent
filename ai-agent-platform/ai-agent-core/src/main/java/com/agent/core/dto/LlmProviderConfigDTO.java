package com.agent.core.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LlmProviderConfigDTO {

    private Long id;

    @NotBlank(message = "提供商名称不能为空")
    private String providerName;

    @NotBlank(message = "API Key 不能为空")
    private String apiKey;

    /** 脱敏后的 API Key，用于前端展示，如 sk-****abcd */
    private String apiKeyMasked;

    /** 是否已配置 API Key */
    private Boolean hasApiKey;

    private String modelName;

    private Integer enabled;
}
