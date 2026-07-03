CREATE TABLE IF NOT EXISTS llm_provider_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    provider_name VARCHAR(32) NOT NULL COMMENT '模型提供商: openai/deepseek/qwen/anthropic',
    api_key VARCHAR(512) NOT NULL DEFAULT '' COMMENT 'API Key',
    model_name VARCHAR(128) DEFAULT NULL COMMENT '默认模型名',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用: 1-启用, 0-禁用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_provider_name (provider_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='LLM 模型提供商配置表';
