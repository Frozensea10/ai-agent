# AI Agent 智能体平台

> 基于 Spring Cloud 微服务架构的企业级 AI Agent 系统，支持多 Agent 协作、RAG 知识库、工具调用、流式对话等核心能力。

## 项目亮点

| 亮点 | 技术实现 |
|------|---------|
| **微服务架构** | Spring Cloud Gateway + Nacos 服务注册发现 + Feign 服务间调用 |
| **智能体协作** | DAG 任务分解 + 拓扑排序调度 + CompletableFuture 异步执行 |
| **RAG 知识库** | Qdrant 向量数据库 + 文档分块 + 语义检索 + 上下文增强 |
| **工具调用** | MCP 协议接入外部工具，支持 HTTP/数据库/代码执行等 |
| **流式对话** | SSE 实时推送 + LangChain4j TokenStream + 前端逐字渲染 |
| **多模型接入** | DeepSeek / 通义千问 可插拔适配器模式 |

## 技术栈

**后端**：Spring Boot 3.2 + Spring Cloud 2022 + Spring Cloud Alibaba  
**AI 框架**：LangChain4j 1.13 + AiServices + TokenStream  
**数据库**：MySQL 8.0 + Redis 7 + Qdrant 向量数据库  
**基础设施**：Docker Compose + MinIO 对象存储  
**前端**：Vue 3 + TypeScript + Element Plus + Vite  
**构建工具**：Maven 多模块 + npm

## 系统架构

```
┌─────────────────────────────────────────────────────────────────┐
│                         前端 Vue 3 (3000)                        │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    API Gateway (8080)                           │
│              Spring Cloud Gateway + JWT 鉴权                     │
└─────────────────────────────────────────────────────────────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        ▼                     ▼                     ▼
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│   User(8081) │    │   Core(8082) │    │  Chat(8083)  │
│   用户认证    │    │  Agent 管理   │    │   对话服务    │
└──────────────┘    └──────────────┘    └──────────────┘
        │                     │                     │
        ▼                     ▼                     ▼
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│Knowledge(8084)│   │  File(8085)  │    │  MCP(8086)   │
│   知识库      │    │   文件服务    │    │   工具调用    │
└──────────────┘    └──────────────┘    └──────────────┘
        │                     │                     │
        └─────────────────────┼─────────────────────┘
                              ▼
              ┌───────────────────────────────┐
              │      MySQL / Redis / Qdrant    │
              │      MinIO / Nacos             │
              └───────────────────────────────┘
```

## 核心功能

### 1. 智能体（Agent）管理

- 创建、配置、启停自定义 Agent
- 支持设置 System Prompt、温度、最大 Token 等参数
- 三种类型：单 Agent / 主调度 Agent / 子 Agent

### 2. 多 Agent 协作（DAG 调度）

- 主 Agent 基于 LLM 将复杂任务分解为子任务 DAG
- 按依赖关系拓扑排序，串行/并行执行子任务
- 动态生成子 Agent，无需预配置能力标签

```java
// 任务分解示例
{
  "tasks": [
    {"taskId": "1", "description": "分析销售数据趋势", "dependencies": []},
    {"taskId": "2", "description": "生成可视化图表", "dependencies": ["1"]},
    {"taskId": "3", "description": "撰写分析报告", "dependencies": ["1", "2"]}
  ]
}
```

### 3. RAG 知识库

- 文档上传（PDF/Word/TXT/Markdown）
- 智能分块 + 向量化嵌入（Qdrant）
- 语义检索 + 上下文增强生成

### 4. 工具调用（MCP 协议）

- HTTP 请求：调用外部 API
- 数据库查询：只读 SQL 执行
- 代码执行：Python / Java 代码运行
- 内置工具：可扩展的自定义工具注册

### 5. 流式对话

- SSE（Server-Sent Events）实时推送
- 逐 Token 渲染，打字机效果
- 支持工具调用中间状态展示

### 6. 对话记忆

- 短期记忆：Redis 缓存最近 N 轮对话
- 长期记忆：向量数据库存储历史对话语义
- 支持滑动窗口和摘要压缩

## 快速开始

### 环境要求

- JDK 17+
- Node.js 18+
- Docker & Docker Compose
- Maven 3.9+
- LLM API Key（DeepSeek / 通义千问）

### 1. 克隆并配置

```bash
git clone https://github.com/your-username/ai-agent.git
cd ai-agent
cp .env.example .env
# 编辑 .env 填入你的 API Key
```

### 2. 启动基础设施

```bash
docker-compose up -d
```

| 服务 | 端口 | 说明 |
|------|------|------|
| MySQL | 3306 | 关系数据库 |
| Redis | 6379 | 缓存/会话 |
| Qdrant | 6333/6334 | 向量数据库 |
| MinIO | 9000/9001 | 对象存储 |
| Nacos | 8848 | 注册中心（可选） |

### 3. 启动后端服务

```bash
# 在项目根目录执行
mvn clean package -DskipTests

# 或使用 IDE 启动各服务
cd ai-agent-platform
mvn -pl ai-agent-gateway spring-boot:run
mvn -pl ai-agent-user spring-boot:run
# ... 其他服务
```

### 4. 启动前端

```bash
cd ai-agent-web
npm install
npm run dev
```

访问 http://localhost:3000

## 项目结构

```
ai-agent/
├── ai-agent-gateway/          # API 网关
├── ai-agent-platform/
│   ├── ai-agent-common/       # 公共模块（工具类、异常、常量）
│   ├── ai-agent-user/         # 用户认证服务
│   ├── ai-agent-core/         # Agent 核心服务（LLM 适配、协作调度）
│   ├── ai-agent-chat/         # 对话服务（SSE 流式、记忆管理）
│   ├── ai-agent-knowledge/    # 知识库服务（RAG、向量检索）
│   ├── ai-agent-file/         # 文件服务（MinIO 对象存储）
│   └── ai-agent-mcp/          # MCP 工具服务（工具注册、执行）
├── ai-agent-web/              # Vue 3 前端
├── docs/                      # 文档（ER 图、架构图）
└── docker-compose.yml         # 基础设施编排
```

## 数据库设计

核心表结构（共 17 张表）：

| 表名 | 说明 |
|------|------|
| `sys_user` | 用户表 |
| `sys_role` / `sys_permission` | RBAC 权限 |
| `agent_config` | Agent 配置 |
| `chat_session` / `chat_message` | 对话会话与消息 |
| `knowledge_base` / `knowledge_document` / `knowledge_chunk` | 知识库 |
| `mcp_server` / `built_in_tool` / `tool_execution_log` | MCP 工具 |
| `sub_agent_execution` | 子 Agent 执行记录 |
| `llm_provider_config` | 多模型配置 |

## API 概览

| 模块 | 接口 | 说明 |
|------|------|------|
| 认证 | `POST /api/v1/auth/register` | 用户注册 |
| 认证 | `POST /api/v1/auth/login` | 用户登录 |
| 对话 | `POST /api/v1/sessions` | 创建会话 |
| 对话 | `GET /api/v1/sessions/{id}/stream` | SSE 流式对话 |
| Agent | `POST /api/v1/agents` | 创建 Agent |
| Agent | `POST /api/v1/agents/{id}/collaborate` | 多 Agent 协作 |
| 知识库 | `POST /api/v1/knowledge/bases` | 创建知识库 |
| 知识库 | `POST /api/v1/knowledge/documents` | 上传文档 |
| MCP | `GET /api/v1/mcp/tools` | 获取工具列表 |
| MCP | `POST /api/v1/mcp/tools/execute` | 执行工具 |

## 性能指标

- 微服务模块：8 个
- 数据库表：17 张
- API 接口：50+ 个
- 流式响应延迟：首 Token < 500ms
- 向量检索延迟：< 100ms

## 后续规划

- [ ] 工作流编排（可视化 DAG 编辑器）
- [ ] Agent 市场（分享与复用）
- [ ] 多租户支持
- [ ] 监控告警（Prometheus + Grafana）
- [ ] 单元测试覆盖率提升至 80%+

## License

MIT
