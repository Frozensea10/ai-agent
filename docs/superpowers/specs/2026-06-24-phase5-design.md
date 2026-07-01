# 第五阶段设计文档：多Agent协作架构与性能优化

## 1. 概述

### 1.1 目标
实现企业级AI Agent系统的多Agent协作架构和核心性能优化，包括：
- 主从协作模式的多Agent架构
- 全局性能优化（缓存、异步、连接池）
- 可观测性基础（健康检查、指标暴露）

### 1.2 范围
本阶段包含两个核心子系统：
1. **多Agent协作引擎**（ai-agent-core模块）
2. **性能优化基础设施**（跨模块优化）

---

## 2. 多Agent协作架构设计

### 2.1 架构模式：主从协作（Master-Slave）

```
用户请求
    │
    ▼
┌─────────────────┐
│   主Agent        │  ← 任务分解、结果汇总
│  (Master Agent)  │
└────────┬────────┘
         │
    ┌────┴────┬────────┬────────┐
    ▼         ▼        ▼        ▼
┌───────┐ ┌───────┐ ┌───────┐ ┌───────┐
│子Agent1│ │子Agent2│ │子Agent3│ │子Agent4│
│代码执行│ │知识检索│ │数据分析│ │网络搜索│
└───────┘ └───────┘ └───────┘ └───────┘
```

### 2.2 核心组件

| 组件 | 职责 | 实现位置 |
|------|------|----------|
| MasterAgent | 接收用户请求，分解任务，调度子Agent，汇总结果 | ai-agent-core |
| SubAgent | 执行特定子任务，返回结构化结果 | ai-agent-core |
| AgentRegistry | 管理Agent注册与发现 | ai-agent-core |
| TaskPlanner | 将用户请求分解为可执行的子任务列表 | ai-agent-core |
| ResultAggregator | 合并多个子Agent的执行结果 | ai-agent-core |

### 2.3 任务分解策略

主Agent使用LLM进行任务分解：

```
用户输入: "帮我分析这份销售数据，找出趋势并生成可视化图表"

任务分解:
1. [数据解析] 读取并解析销售数据文件
2. [数据分析] 计算月度/季度销售趋势
3. [图表生成] 生成趋势可视化图表
4. [报告撰写] 汇总分析结果生成报告

子Agent分配:
- 子Agent-数据分析 → 任务2
- 子Agent-代码执行 → 任务3（Python matplotlib）
- 子Agent-报告生成 → 任务4
```

### 2.4 数据流

```
1. 用户发送请求到主Agent
2. 主Agent调用TaskPlanner分解任务
3. 主Agent并行调用多个子Agent
4. 子Agent通过MCP工具执行具体任务
5. ResultAggregator收集所有结果
6. 主Agent生成最终回复
```

---

## 3. 性能优化设计

### 3.1 缓存策略

| 缓存层级 | 技术 | 用途 | TTL |
|----------|------|------|-----|
| 本地缓存 | Caffeine | Agent配置、工具元数据 | 5分钟 |
| 分布式缓存 | Redis | 会话状态、用户Token | 30分钟 |
| 向量缓存 | Redis | 知识库检索结果 | 10分钟 |

### 3.2 异步处理

- **@Async线程池**：MCP工具调用、文档向量化
- **CompletableFuture**：并行子Agent执行
- **Spring Event**：解耦模块间通信

### 3.3 连接池优化

| 资源 | 优化措施 |
|------|----------|
| 数据库 | HikariCP连接池参数调优 |
| Redis | Lettuce连接池配置 |
| HTTP客户端 | 连接复用、超时配置 |
| MCP连接 | 长连接保活、自动重连 |

---

## 4. 可观测性设计

### 4.1 健康检查

- Spring Boot Actuator健康端点
- 自定义HealthIndicator（MCP连接、向量数据库）

### 4.2 指标暴露

- Micrometer + Prometheus格式指标
- 关键指标：请求延迟、Token消耗、工具调用次数

---

## 5. 接口设计

### 5.1 多Agent协作API

```
POST /api/v1/agents/{agentId}/collaborate
Request:
{
  "message": "分析销售数据并生成图表",
  "context": { ... },
  "subAgents": ["data-analysis", "code-execution"]
}

Response:
{
  "code": 200,
  "data": {
    "finalAnswer": "...",
    "subTasks": [
      {"agent": "data-analysis", "result": "...", "status": "success"},
      {"agent": "code-execution", "result": "...", "status": "success"}
    ],
    "executionTimeMs": 5000
  }
}
```

### 5.2 子Agent管理API

```
GET /api/v1/agents/sub-agents          # 列出所有子Agent
POST /api/v1/agents/sub-agents         # 注册子Agent
DELETE /api/v1/agents/sub-agents/{id}  # 注销子Agent
```

---

## 6. 数据库变更

### 6.1 新增表

```sql
-- Agent配置表（扩展）
ALTER TABLE agent_config ADD COLUMN agent_type VARCHAR(20) DEFAULT 'single'; -- single/master/sub
ALTER TABLE agent_config ADD COLUMN parent_agent_id BIGINT;
ALTER TABLE agent_config ADD COLUMN capabilities JSON; -- 能力标签
ALTER TABLE agent_config ADD COLUMN priority INT DEFAULT 0; -- 执行优先级

-- 子Agent执行记录表
CREATE TABLE sub_agent_execution (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR(64) NOT NULL,
    master_agent_id BIGINT NOT NULL,
    sub_agent_id BIGINT NOT NULL,
    task_description TEXT,
    task_status VARCHAR(20), -- pending/running/success/failed
    task_result TEXT,
    execution_time_ms INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

---

## 7. 前端变更

### 7.1 Agent配置页面

- 新增"Agent类型"选择（单Agent/主Agent/子Agent）
- 主Agent配置：关联子Agent列表
- 子Agent配置：能力标签、执行优先级

### 7.2 对话页面

- 展示多Agent协作过程（任务分解、执行进度）
- 子任务执行状态可视化

---

## 8. 实施计划

### 批次1：多Agent协作核心
1. Agent类型扩展（单Agent/主Agent/子Agent）
2. TaskPlanner任务分解实现
3. SubAgent并行执行引擎
4. ResultAggregator结果汇总

### 批次2：性能优化
1. Caffeine本地缓存集成
2. @Async异步线程池配置
3. 连接池参数调优
4. 缓存注解应用到核心服务

### 批次3：可观测性
1. Actuator健康检查扩展
2. Micrometer指标暴露
3. 自定义业务指标

---

## 9. 风险评估

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 任务分解准确性 | 高 | 使用LLM进行分解，提供示例模板 |
| 子Agent执行超时 | 中 | 设置超时时间，支持部分结果返回 |
| 缓存一致性 | 低 | 使用CacheEvict注解，配置合理TTL |

---

*设计日期：2026-06-24*
*版本：v1.0*
