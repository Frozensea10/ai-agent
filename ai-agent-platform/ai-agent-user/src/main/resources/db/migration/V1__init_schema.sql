SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- 用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(100),
    phone VARCHAR(20),
    avatar_url VARCHAR(255),
    status TINYINT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

-- 角色表
CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_name VARCHAR(50) NOT NULL,
    role_code VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255)
);

-- 权限表
CREATE TABLE IF NOT EXISTS sys_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    permission_name VARCHAR(50) NOT NULL,
    permission_code VARCHAR(100) NOT NULL UNIQUE,
    resource_type VARCHAR(20),
    http_method VARCHAR(10),
    url_pattern VARCHAR(255)
);

-- 用户角色关联表
CREATE TABLE IF NOT EXISTS sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id)
);

-- 角色权限关联表
CREATE TABLE IF NOT EXISTS sys_role_permission (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id)
);

-- 初始化角色数据
INSERT INTO sys_role (role_name, role_code, description) VALUES
('管理员', 'ADMIN', '系统管理员，拥有所有权限'),
('普通用户', 'USER', '普通用户，拥有基本权限')
ON DUPLICATE KEY UPDATE role_name = role_name;

-- 初始化权限数据
INSERT INTO sys_permission (permission_name, permission_code, resource_type, http_method, url_pattern) VALUES
('用户管理', 'user:manage', 'api', '*', '/api/v1/user/**'),
('Agent管理', 'agent:manage', 'api', '*', '/api/v1/agents/**'),
('知识库管理', 'kb:manage', 'api', '*', '/api/v1/knowledge-bases/**'),
('对话管理', 'chat:manage', 'api', '*', '/api/v1/sessions/**')
ON DUPLICATE KEY UPDATE permission_name = permission_name;

-- 关联管理员角色权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p WHERE r.role_code = 'ADMIN'
ON DUPLICATE KEY UPDATE role_id = role_id;

-- 关联普通用户角色权限（只给对话权限）
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p WHERE r.role_code = 'USER' AND p.permission_code = 'chat:manage'
ON DUPLICATE KEY UPDATE role_id = role_id;

-- MCP Server 配置表
CREATE TABLE IF NOT EXISTS mcp_server (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    server_name VARCHAR(100) NOT NULL,
    server_type VARCHAR(20),
    transport_config JSON,
    status VARCHAR(20) DEFAULT 'active',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

-- 内置工具表
CREATE TABLE IF NOT EXISTS built_in_tool (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tool_name VARCHAR(100) NOT NULL,
    tool_code VARCHAR(50) NOT NULL UNIQUE,
    description TEXT,
    tool_type VARCHAR(50),
    config_schema JSON,
    status VARCHAR(20) DEFAULT 'enabled',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

-- 工具执行日志表
CREATE TABLE IF NOT EXISTS tool_execution_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tool_code VARCHAR(50) NOT NULL,
    tool_type VARCHAR(50),
    request_params TEXT,
    response_result TEXT,
    status VARCHAR(20),
    error_message TEXT,
    execute_time_ms BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 添加 MCP 管理权限
INSERT INTO sys_permission (permission_name, permission_code, resource_type, http_method, url_pattern) VALUES
('MCP工具管理', 'mcp:manage', 'api', '*', '/api/v1/mcp/**')
ON DUPLICATE KEY UPDATE permission_name = permission_name;

-- 关联管理员角色 MCP 权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p WHERE r.role_code = 'ADMIN' AND p.permission_code = 'mcp:manage'
ON DUPLICATE KEY UPDATE role_id = role_id;

-- Agent 配置表
CREATE TABLE IF NOT EXISTS agent_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    agent_name VARCHAR(100) NOT NULL,
    agent_code VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255),
    model_provider VARCHAR(50),
    model_name VARCHAR(100),
    system_prompt TEXT,
    temperature DOUBLE DEFAULT 0.7,
    max_tokens INT DEFAULT 2048,
    memory_type VARCHAR(20) DEFAULT 'window',
    memory_max_messages INT DEFAULT 10,
    agent_type VARCHAR(20) DEFAULT 'single',
    parent_agent_id BIGINT,
    capabilities JSON,
    priority INT DEFAULT 0,
    status TINYINT DEFAULT 1,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

-- 初始化默认 Agent（通用助手）
INSERT INTO agent_config (id, agent_name, agent_code, description, model_provider, model_name, system_prompt, temperature, max_tokens, memory_type, memory_max_messages, agent_type, status, created_by)
VALUES (1, '通用助手', 'default-assistant', '默认通用 AI 助手，可回答日常问题', 'openai', 'gpt-3.5-turbo', '你是一个 helpful 的 AI 助手。', 0.7, 2048, 'window', 10, 'single', 1, 1)
ON DUPLICATE KEY UPDATE agent_name = agent_name;

-- 会话表
CREATE TABLE IF NOT EXISTS chat_session (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR(64) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    agent_id BIGINT,
    kb_id BIGINT,
    session_title VARCHAR(255),
    message_count INT DEFAULT 0,
    status TINYINT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

-- 消息表
CREATE TABLE IF NOT EXISTS chat_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR(64) NOT NULL,
    message_id VARCHAR(64) NOT NULL UNIQUE,
    role VARCHAR(20) NOT NULL,
    content TEXT,
    content_type VARCHAR(20) DEFAULT 'text',
    tokens_used INT,
    model_name VARCHAR(100),
    tool_calls JSON,
    parent_message_id VARCHAR(64),
    status TINYINT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);
