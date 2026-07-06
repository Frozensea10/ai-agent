-- 重新初始化默认 Agent（通用助手）
-- 背景：之前修复 MySQL utf8mb4 字符集时清空了数据卷，V1 迁移已记录执行过不会重跑，
-- 导致 agent_config 表缺少 id=1 的 default-assistant，前端 ChatView 硬编码 DEFAULT_AGENT_ID=1 会报错。
-- 另：用户可能在 Agent 管理页面误删 default-assistant，导致 deleted=1（软删除），
-- MyBatis-Plus 逻辑删除会过滤 deleted=1 的记录，使 selectById(1) 返回 null。
-- 本迁移通过 ON DUPLICATE KEY UPDATE 保证幂等，并显式重置 deleted=0、status=1，确保记录可用。
INSERT INTO agent_config (id, agent_name, agent_code, description, model_provider, model_name, system_prompt, temperature, max_tokens, memory_type, memory_max_messages, agent_type, status, created_by)
VALUES (1, '通用助手', 'default-assistant', '默认通用 AI 助手，可回答日常问题', 'openai', 'gpt-3.5-turbo', '你是一个 helpful 的 AI 助手。', 0.7, 2048, 'window', 10, 'single', 1, 1)
ON DUPLICATE KEY UPDATE
    agent_name = VALUES(agent_name),
    deleted = 0,
    status = 1;
