-- 子Agent执行记录表
-- 记录主Agent调用子Agent执行任务的完整生命周期
CREATE TABLE IF NOT EXISTS sub_agent_execution (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR(64) NOT NULL COMMENT '会话ID',
    master_agent_id BIGINT NOT NULL COMMENT '主Agent ID',
    sub_agent_id BIGINT COMMENT '子Agent ID',
    task_description TEXT COMMENT '任务描述',
    task_status VARCHAR(20) COMMENT '任务状态: pending(待执行), running(执行中), success(成功), failed(失败)',
    task_result TEXT COMMENT '任务执行结果',
    execution_time_ms INT COMMENT '执行耗时(ms)',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
