package com.agent.core.dto;

import lombok.Data;

@Data
public class LlmProviderConfigDTO {

    private String providerName;

    private String apiKey;

    private String modelName;

    private Integer enabled;
}
