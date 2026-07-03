package com.agent.core.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("llm_provider_config")
public class LlmProviderConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String providerName;

    private String apiKey;

    private String modelName;

    private Integer enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
