-- 修改默认 Agent 使用 DeepSeek 模型，避免默认依赖 OpenAI
UPDATE agent_config
SET model_provider = 'deepseek', model_name = 'deepseek-chat'
WHERE id = 1 AND agent_code = 'default-assistant';
