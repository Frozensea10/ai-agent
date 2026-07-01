# AI Agent 智能助手系统 — 企业级项目开发计划书

## 一、项目概述与目标

### 1.1 项目背景

2026 年被公认为企业级 AI Agent 规模化应用的关键拐点。企业对 AI Agent 的态度已从"尝试性探索"转向"规模化应用"，技术叙事让位于实际业务价值。MCP（Model Context Protocol）协议已成为行业标准，LangChain4j 1.13.x 已成熟稳定，向量数据库市场日趋完善，为构建企业级 AI Agent 系统提供了坚实的技术底座。

### 1.2 项目目标

构建一套面向软件工程学生的 AI Agent 智能助手系统，具备以下核心能力：

- **智能对话能力**：基于大模型的多轮对话，支持流式响应
- **知识库管理**：支持文档上传、解析、向量化存储与检索（RAG）
- **工具调用能力**：通过 MCP 协议集成外部工具（代码执行、数据库查询、API 调用等）
- **多 Agent 协作**：主 Agent + 子 Agent 的多层架构，实现复杂任务分解
- **用户与权限管理**：完整的用户体系、角色权限、会话管理

### 1.3 项目定位

- **毕业设计级**：技术深度足够，覆盖前后端分离、微服务、AI 集成等全栈技术
- **企业级参考**：架构设计遵循企业级标准，具备可扩展性和可维护性
- **教学价值**：代码结构清晰、文档完善，适合作为学习案例

---

## 二、技术架构设计

### 2.1 总体架构

采用**前后端分离 + 微服务架构**，整体分为四层：

```
┌─────────────────────────────────────────────────────────┐
│                    前端展示层 (Vue3)                      │
│         ┌─────────────┐    ┌─────────────┐              │
│         │  用户管理界面  │    │  对话交互界面  │              │
│         │  知识库管理   │    │  Agent配置   │              │
│         └─────────────┘    └─────────────┘              │
├─────────────────────────────────────────────────────────┤
│                   API网关层 (Spring Cloud Gateway)        │
│         统一认证 │ 限流熔断 │ 路由转发 │ 日志监控          │
├─────────────────────────────────────────────────────────┤
│                   业务服务层 (Spring Boot 3.x)           │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐   │
│  │ 用户服务  │ │ Agent核心 │ │ 知识库服务 │ │ 对话服务  │   │
│  │  user-svc │ │ agent-svc │ │  kb-svc  │ │ chat-svc │   │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘   │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐                │
│  │ MCP工具   │ │ 文件服务  │ │ 通知服务  │                │
│  │ mcp-svc  │ │ file-svc │ │ notify   │                │
│  └──────────┘ └──────────┘ └──────────┘                │
├─────────────────────────────────────────────────────────┤
│                   数据存储层                              │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐   │
│  │  MySQL   │ │  Redis   │ │ 向量数据库 │ │  MinIO   │   │
│  │ (关系数据) │ │ (缓存/会话)│ │(知识检索) │ │ (文件存储)│   │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘   │
├─────────────────────────────────────────────────────────┤
│                   AI能力层 (LangChain4j)                 │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐   │
│  │ 大模型接入 │ │ 嵌入模型  │ │ 向量检索  │ │ 工具调用  │   │
│  │ ChatModel│ │Embedding │ │Retriever │ │  MCP     │   │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘   │
└─────────────────────────────────────────────────────────┘
```

### 2.2 技术栈选型

| 层级 | 技术选型 | 版本 | 选型理由 |
|------|----------|------|----------|
| **前端** | Vue 3 | 3.4.x | 组合式 API，TypeScript 原生支持，性能优异 |
| | Element Plus | 2.7.x | 企业级 UI 组件库，与 Vue3 深度适配 |
| | Pinia | 2.1.x | 状态管理，TypeScript 友好 |
| | Axios | 1.7.x | HTTP 请求，支持拦截器和流式响应 |
| | marked | 最新 | Markdown 渲染，支持代码高亮 |
| | highlight.js | 最新 | 代码语法高亮 |
| **网关** | Spring Cloud Gateway | 4.1.x | Spring 生态官方网关，支持 Reactive 编程 |
| | Nacos | 2.3.x | 服务注册发现与配置中心 |
| **后端** | Spring Boot | 3.2.x | Java 17+，原生支持虚拟线程，性能提升 |
| | Spring Security | 6.2.x | 认证授权，JWT 支持 |
| | MyBatis-Plus | 3.5.x | 简化 CRUD，支持多租户 |
| | LangChain4j | 1.13.x | Java 专属 AI 框架，Agent 编排成熟，MCP 支持完善 |
| | Spring AI | 1.0.x | 备选方案，Spring 生态原生 AI 集成 |
| **AI 模型** | 大语言模型 | 多模型适配 | 支持 OpenAI、DeepSeek、通义千问、Claude 等 |
| | 嵌入模型 | text-embedding-3 | 1536 维向量，支持多语言 |
| **向量数据库** | Qdrant | 1.10.x | 开源、高性能、支持混合检索，适合自托管 |
| | pgvector | 0.7.x | 备选，适合已有 PostgreSQL 的团队 |
| **关系数据库** | MySQL | 8.0.x | 成熟稳定，支持 JSON 类型 |
| **缓存** | Redis | 7.2.x | 会话存储、限流、缓存 |
| **消息队列** | RabbitMQ | 3.12.x | 异步任务、事件驱动 |
| **文件存储** | MinIO | 最新 | 兼容 S3 API，本地部署 |
| **容器化** | Docker | 24.x | 服务容器化 |
| **编排** | Kubernetes | 1.29.x | 生产环境容器编排 |
| **监控** | Prometheus + Grafana | 最新 | 指标采集与可视化 |
| **日志** | ELK Stack | 8.x | 日志收集与分析 |

### 2.3 前后端分离架构

**前端架构**：
- 采用 Vue 3 + Vite 构建，TypeScript 全量覆盖
- 基于 Element Plus 的自定义主题
- 路由懒加载，组件按需引入
- Axios 封装：统一错误处理、Token 刷新、请求重试
- SSE 客户端封装：支持流式对话的实时接收

**后端架构**：
- 多模块 Maven 项目，每个微服务独立部署
- 统一响应格式：RESTful API + 标准 JSON 响应体
- 全局异常处理：分层异常体系，统一错误码
- 接口文档：SpringDoc OpenAPI（Swagger 3.0）

---

## 三、功能模块拆分

### 3.1 模块划分总览

| 模块 | 服务名 | 职责 | 核心功能 |
|------|--------|------|----------|
| 用户管理 | `user-service` | 用户、角色、权限、认证 | 注册登录、JWT 认证、RBAC 权限 |
| Agent 核心 | `agent-core-service` | Agent 配置、模型管理、工具编排 | Agent 定义、模型切换、Prompt 管理 |
| 知识库 | `knowledge-service` | 文档管理、向量化、检索 | 上传解析、分块嵌入、RAG 检索 |
| 对话系统 | `chat-service` | 会话管理、消息流式传输 | 多轮对话、历史记录、流式响应 |
| MCP 工具 | `mcp-tool-service` | MCP Server 管理、工具调用 | 工具注册、协议适配、执行调度 |
| 文件服务 | `file-service` | 文件上传、存储、预览 | 大文件分片、MinIO 存储、格式转换 |
| 通知服务 | `notification-service` | 消息推送、邮件通知 | WebSocket 推送、邮件模板 |

### 3.2 用户管理模块（user-service）

**功能点**：
- 用户注册/登录（支持用户名密码、OAuth2.0 第三方登录）
- JWT Token 认证（Access Token + Refresh Token 双 Token 机制）
- RBAC 权限模型（用户-角色-权限三级结构）
- 用户信息管理（头像、昵称、密码修改）
- API Key 管理（为每个用户生成调用 AI 服务的 API Key）

**数据库表**：
- `sys_user`：用户基础信息
- `sys_role`：角色定义
- `sys_permission`：权限定义
- `sys_user_role`：用户角色关联
- `sys_role_permission`：角色权限关联
- `user_api_key`：用户 API Key

### 3.3 Agent 核心模块（agent-core-service）

**功能点**：
- Agent 配置管理（创建、编辑、删除 Agent）
- 大模型配置管理（支持多模型切换：OpenAI、DeepSeek、通义千问等）
- Prompt 模板管理（系统提示词、角色设定）
- 记忆管理（ChatMemoryProvider，支持窗口记忆和摘要记忆）
- Agent 行为监控（调用次数、响应时间、Token 消耗）

**核心组件**：
- `ChatModel`：大模型客户端封装
- `StreamingChatModel`：流式大模型客户端
- `ChatMemoryProvider`：对话记忆提供者
- `ContentRetriever`：内容检索器（对接知识库）
- `ToolProvider`：工具提供者（对接 MCP）

### 3.4 知识库模块（knowledge-service）

**功能点**：
- 知识库空间管理（创建知识库、设置权限）
- 文档上传与解析（支持 PDF、Word、TXT、Markdown、Excel）
- 文档分块策略（按段落、按 Token、按语义）
- 向量化处理（调用 Embedding 模型生成向量）
- 向量存储（写入 Qdrant/pgvector）
- RAG 检索（相似度搜索 + 元数据过滤）
- 知识库版本管理（文档更新、版本回滚）

**文档处理流程**：
```
文档上传 → 格式解析 → 文本提取 → 文档分块 → 嵌入向量化 → 向量存储 → 索引构建
```

**RAG 检索流程**：
```
用户查询 → 查询向量化 → 向量相似度检索 → 重排序(Rerank) → 上下文组装 → 生成回答
```

**2026 年技术趋势融入**：
- 支持 GraphRAG（知识图谱增强 RAG），提升跨文档推理能力
- 混合检索：稠密向量 + 稀疏向量（BM25）融合
- 多向量索引：同一文档存储多种向量表示

### 3.5 对话系统模块（chat-service）

**功能点**：
- 会话管理（创建会话、删除会话、会话列表）
- 消息管理（发送消息、接收流式响应、消息历史）
- 多轮对话支持（基于 MemoryId 的上下文关联）
- 对话流式输出（SSE 协议，逐字显示效果）
- 对话中断与重试
- 对话评价与反馈（点赞/点踩、问题反馈）

**流式对话技术方案**：
- 后端：Spring WebFlux + `Flux<String>` 流式返回
- 前端：EventSource API 接收 SSE 事件，逐字渲染
- 支持对话中断：客户端发送中断信号，后端取消 Flux 订阅

**SSE vs WebSocket 选型**：
- 本项目采用 SSE：纯文本单向流式输出场景，SSE 更轻量、与 HTTP 兼容、自动重连
- WebSocket 仅用于实时通知推送（如系统消息）

### 3.6 MCP 工具集成模块（mcp-tool-service）

**功能点**：
- MCP Server 注册与管理（添加、删除、启用/禁用）
- MCP 工具发现（自动列出 Server 提供的工具列表）
- 工具调用执行（通过 MCP 协议调用外部工具）
- 工具执行结果处理（格式化、错误处理）
- 内置工具集（代码执行、数据库查询、文件操作、网络请求）

**MCP 协议集成**：
- 支持 HTTP 传输模式（SSE 通道 + HTTP POST 命令）
- 支持 Stdio 传输模式（本地子进程）
- 通过 LangChain4j 的 `McpToolProvider` 集成

**典型 MCP 工具示例**：
- 代码执行工具（Python/Java 代码沙箱执行）
- 数据库查询工具（SQL 查询、结果格式化）
- 天气查询工具（调用第三方天气 API）
- 搜索引擎工具（网络信息检索）

### 3.7 文件服务模块（file-service）

**功能点**：
- 文件上传（支持大文件分片上传）
- 文件存储（MinIO 对象存储）
- 文件预览（PDF 在线预览、图片缩略图）
- 文档解析（调用 Apache Tika、PDFBox 等库提取文本）

### 3.8 通知服务模块（notification-service）

**功能点**：
- WebSocket 实时推送（系统通知、对话状态更新）
- 邮件通知（注册验证、密码重置）
- 消息队列集成（RabbitMQ 异步处理）

---

## 四、数据库设计

### 4.1 关系型数据库（MySQL）

**核心表结构**：

```sql
-- 用户表
CREATE TABLE sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(100),
    phone VARCHAR(20),
    avatar_url VARCHAR(255),
    status TINYINT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 角色表
CREATE TABLE sys_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_name VARCHAR(50) NOT NULL,
    role_code VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255)
);

-- 权限表
CREATE TABLE sys_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    permission_name VARCHAR(50) NOT NULL,
    permission_code VARCHAR(100) NOT NULL UNIQUE,
    resource_type VARCHAR(20),
    http_method VARCHAR(10),
    url_pattern VARCHAR(255)
);

-- Agent 配置表
CREATE TABLE agent_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    agent_name VARCHAR(100) NOT NULL,
    agent_code VARCHAR(50) NOT NULL UNIQUE,
    description TEXT,
    model_provider VARCHAR(50), -- openai/deepseek/qwen
    model_name VARCHAR(100),
    system_prompt TEXT,
    temperature DECIMAL(3,2) DEFAULT 0.7,
    max_tokens INT DEFAULT 2048,
    memory_type VARCHAR(20) DEFAULT 'window', -- window/summary
    memory_max_messages INT DEFAULT 10,
    status TINYINT DEFAULT 1,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 知识库表
CREATE TABLE knowledge_base (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    kb_name VARCHAR(100) NOT NULL,
    kb_code VARCHAR(50) NOT NULL UNIQUE,
    description TEXT,
    embedding_model VARCHAR(100),
    vector_dimension INT DEFAULT 1536,
    chunk_size INT DEFAULT 500,
    chunk_overlap INT DEFAULT 50,
    status TINYINT DEFAULT 1,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 文档表
CREATE TABLE kb_document (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    kb_id BIGINT NOT NULL,
    doc_name VARCHAR(255) NOT NULL,
    doc_type VARCHAR(20), -- pdf/word/txt/md
    file_size BIGINT,
    file_path VARCHAR(500),
    chunk_count INT DEFAULT 0,
    vector_status TINYINT DEFAULT 0, -- 0:未向量化 1:处理中 2:已完成 3:失败
    version INT DEFAULT 1,
    status TINYINT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 会话表
CREATE TABLE chat_session (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR(64) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    agent_id BIGINT,
    kb_id BIGINT,
    session_title VARCHAR(255),
    message_count INT DEFAULT 0,
    status TINYINT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 消息表
CREATE TABLE chat_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR(64) NOT NULL,
    message_id VARCHAR(64) NOT NULL UNIQUE,
    role VARCHAR(20) NOT NULL, -- user/assistant/system
    content TEXT,
    content_type VARCHAR(20) DEFAULT 'text', -- text/markdown/code
    tokens_used INT,
    model_name VARCHAR(100),
    tool_calls JSON, -- MCP 工具调用记录
    parent_message_id VARCHAR(64),
    status TINYINT DEFAULT 1, -- 1:正常 2:中断 3:错误
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- MCP Server 配置表
CREATE TABLE mcp_server (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    server_name VARCHAR(100) NOT NULL,
    server_type VARCHAR(20), -- http/stdio
    transport_config JSON, -- 传输配置
    tools_config JSON, -- 工具配置
    status TINYINT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 4.2 向量数据库（Qdrant）

**集合设计**：

```json
// 知识库文档向量集合
{
  "collection_name": "kb_documents",
  "vectors_config": {
    "size": 1536,
    "distance": "Cosine"
  },
  "quantization_config": {
    "scalar": {
      "type": "int8",
      "always_ram": true
    }
  },
  "payload_schema": {
    "kb_id": "keyword",
    "doc_id": "keyword",
    "chunk_index": "integer",
    "doc_type": "keyword",
    "source": "keyword",
    "created_at": "datetime"
  }
}
```

**向量存储结构**：
- `id`: 向量唯一 ID（格式：`{doc_id}_{chunk_index}`）
- `vector`: 1536 维浮点向量
- `payload`:
  - `kb_id`: 知识库 ID
  - `doc_id`: 文档 ID
  - `chunk_index`: 分块索引
  - `text`: 原始文本内容
  - `doc_type`: 文档类型
  - `source`: 来源标识
  - `created_at`: 创建时间

### 4.3 缓存设计（Redis）

**Key 命名规范**：
- `user:token:{userId}`: 用户 Token
- `user:refresh:{userId}`: Refresh Token
- `session:history:{sessionId}`: 会话历史消息（List 结构）
- `rate_limit:{userId}:{api}`: API 限流计数
- `kb:vector:status:{docId}`: 文档向量化状态
- `agent:config:{agentId}`: Agent 配置缓存

---

## 五、API 设计规范

### 5.1 统一响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": {},
  "timestamp": 1719820800000,
  "traceId": "abc123def456"
}
```

**错误码规范**：
| 错误码 | 含义 | 说明 |
|--------|------|------|
| 200 | 成功 | 请求处理成功 |
| 400 | 参数错误 | 请求参数校验失败 |
| 401 | 未认证 | Token 无效或过期 |
| 403 | 无权限 | 权限不足 |
| 404 | 资源不存在 | 请求的资源不存在 |
| 429 | 请求过于频繁 | 触发限流 |
| 500 | 系统错误 | 服务器内部错误 |
| 501 | AI 服务错误 | 大模型调用失败 |
| 502 | 向量服务错误 | 向量数据库操作失败 |

### 5.2 核心 API 列表

**用户管理**：
- `POST /api/v1/auth/register` - 用户注册
- `POST /api/v1/auth/login` - 用户登录
- `POST /api/v1/auth/refresh` - Token 刷新
- `GET /api/v1/user/profile` - 获取用户信息
- `PUT /api/v1/user/profile` - 更新用户信息
- `GET /api/v1/user/api-keys` - 获取 API Key 列表
- `POST /api/v1/user/api-keys` - 生成新 API Key

**Agent 管理**：
- `GET /api/v1/agents` - 获取 Agent 列表
- `POST /api/v1/agents` - 创建 Agent
- `GET /api/v1/agents/{id}` - 获取 Agent 详情
- `PUT /api/v1/agents/{id}` - 更新 Agent
- `DELETE /api/v1/agents/{id}` - 删除 Agent
- `POST /api/v1/agents/{id}/test` - 测试 Agent 对话

**知识库**：
- `GET /api/v1/knowledge-bases` - 获取知识库列表
- `POST /api/v1/knowledge-bases` - 创建知识库
- `GET /api/v1/knowledge-bases/{id}` - 获取知识库详情
- `DELETE /api/v1/knowledge-bases/{id}` - 删除知识库
- `POST /api/v1/knowledge-bases/{id}/documents` - 上传文档
- `GET /api/v1/knowledge-bases/{id}/documents` - 获取文档列表
- `DELETE /api/v1/knowledge-bases/{id}/documents/{docId}` - 删除文档
- `POST /api/v1/knowledge-bases/{id}/search` - 知识库检索

**对话系统**：
- `GET /api/v1/sessions` - 获取会话列表
- `POST /api/v1/sessions` - 创建会话
- `DELETE /api/v1/sessions/{sessionId}` - 删除会话
- `GET /api/v1/sessions/{sessionId}/messages` - 获取消息历史
- `POST /api/v1/sessions/{sessionId}/messages` - 发送消息（非流式）
- `GET /api/v1/sessions/{sessionId}/stream` - 流式对话（SSE）
- `POST /api/v1/sessions/{sessionId}/stop` - 中断对话

**MCP 工具**：
- `GET /api/v1/mcp/servers` - 获取 MCP Server 列表
- `POST /api/v1/mcp/servers` - 注册 MCP Server
- `DELETE /api/v1/mcp/servers/{id}` - 删除 MCP Server
- `GET /api/v1/mcp/servers/{id}/tools` - 获取工具列表
- `POST /api/v1/mcp/tools/{toolId}/execute` - 执行工具

### 5.3 流式对话 API 设计

**SSE 流式响应**：

```
GET /api/v1/sessions/{sessionId}/stream?message={message}

Response Headers:
Content-Type: text/event-stream
Cache-Control: no-cache
Connection: keep-alive

Response Body:
data: {"type":"start","messageId":"msg_123"}

data: {"type":"content","delta":"你好","finishReason":null}

data: {"type":"content","delta":"，","finishReason":null}

data: {"type":"content","delta":"我是 AI 助手","finishReason":null}

data: {"type":"tool_call","toolName":"weather_query","arguments":"{\"city\":\"北京\"}"}

data: {"type":"tool_result","toolName":"weather_query","result":"..."}

data: {"type":"content","delta":"北京今天天气晴朗","finishReason":null}

data: {"type":"end","messageId":"msg_123","usage":{"promptTokens":100,"completionTokens":50}}
```

---

## 六、开发规范与代码结构

### 6.1 后端项目结构（Maven 多模块）

```
ai-agent-platform/
├── pom.xml                          # 父 POM，统一管理依赖版本
├── ai-agent-common/                 # 公共模块
│   ├── src/main/java/
│   │   └── com/agent/common/
│   │       ├── config/              # 公共配置
│   │       ├── constants/           # 常量定义
│   │       ├── dto/                 # 公共 DTO
│   │       ├── exception/           # 异常定义
│   │       ├── result/              # 统一响应封装
│   │       ├── utils/               # 工具类
│   │       └── enums/               # 枚举定义
│   └── pom.xml
├── ai-agent-gateway/                # 网关服务
│   ├── src/main/java/
│   │   └── com/agent/gateway/
│   │       ├── filter/              # 网关过滤器
│   │       ├── config/              # 网关配置
│   │       └── handler/             # 异常处理
│   └── pom.xml
├── ai-agent-user/                   # 用户服务
│   ├── src/main/java/
│   │   └── com/agent/user/
│   │       ├── controller/          # 控制器层
│   │       ├── service/             # 服务层
│   │       │   └── impl/
│   │       ├── mapper/              # 数据访问层
│   │       ├── entity/              # 实体类
│   │       ├── dto/                 # 数据传输对象
│   │       ├── vo/                  # 视图对象
│   │       └── config/              # 服务配置
│   └── pom.xml
├── ai-agent-core/                   # Agent 核心服务
│   ├── src/main/java/
│   │   └── com/agent/core/
│   │       ├── controller/
│   │       ├── service/
│   │       ├── agent/               # Agent 引擎
│   │       │   ├── factory/         # Agent 工厂
│   │       │   ├── executor/        # 执行器
│   │       │   ├── memory/          # 记忆管理
│   │       │   └── tool/            # 工具集成
│   │       ├── llm/                 # 大模型封装
│   │       │   ├── adapter/         # 模型适配器
│   │       │   └── config/          # 模型配置
│   │       └── config/
│   └── pom.xml
├── ai-agent-knowledge/              # 知识库服务
│   ├── src/main/java/
│   │   └── com/agent/knowledge/
│   │       ├── controller/
│   │       ├── service/
│   │       ├── document/            # 文档处理
│   │       │   ├── parser/          # 文档解析器
│   │       │   ├── chunker/         # 分块策略
│   │       │   └── extractor/       # 内容提取
│   │       ├── vector/              # 向量操作
│   │       │   ├── store/           # 向量存储
│   │       │   └── search/          # 向量检索
│   │       └── rag/                 # RAG 引擎
│   └── pom.xml
├── ai-agent-chat/                   # 对话服务
│   ├── src/main/java/
│   │   └── com/agent/chat/
│   │       ├── controller/
│   │       ├── service/
│   │       ├── session/             # 会话管理
│   │       ├── message/             # 消息处理
│   │       └── stream/              # 流式处理
│   └── pom.xml
├── ai-agent-mcp/                    # MCP 工具服务
│   ├── src/main/java/
│   │   └── com/agent/mcp/
│   │       ├── controller/
│   │       ├── service/
│   │       ├── client/              # MCP 客户端
│   │       ├── server/              # MCP Server 管理
│   │       └── tool/                # 工具实现
│   └── pom.xml
└── ai-agent-file/                   # 文件服务
    └── ...
```

### 6.2 前端项目结构（Vue3）

```
ai-agent-web/
├── public/
├── src/
│   ├── api/                         # API 接口封装
│   │   ├── user.ts
│   │   ├── agent.ts
│   │   ├── knowledge.ts
│   │   ├── chat.ts
│   │   └── mcp.ts
│   ├── assets/                      # 静态资源
│   ├── components/                  # 公共组件
│   │   ├── common/                  # 通用组件
│   │   ├── chat/                    # 对话相关组件
│   │   │   ├── ChatMessage.vue
│   │   │   ├── ChatInput.vue
│   │   │   ├── ChatStream.vue
│   │   │   └── CodeBlock.vue
│   │   └── knowledge/               # 知识库组件
│   ├── composables/                 # 组合式函数
│   │   ├── useChat.ts
│   │   ├── useSSE.ts
│   │   └── useAuth.ts
│   ├── layouts/                     # 布局组件
│   ├── router/                      # 路由配置
│   ├── stores/                      # Pinia 状态管理
│   │   ├── user.ts
│   │   ├── chat.ts
│   │   └── agent.ts
│   ├── styles/                      # 全局样式
│   ├── types/                       # TypeScript 类型定义
│   ├── utils/                       # 工具函数
│   │   ├── request.ts               # Axios 封装
│   │   ├── sse.ts                   # SSE 客户端封装
│   │   └── format.ts
│   ├── views/                       # 页面视图
│   │   ├── login/
│   │   ├── dashboard/
│   │   ├── agent/
│   │   ├── knowledge/
│   │   ├── chat/
│   │   └── settings/
│   ├── App.vue
│   └── main.ts
├── package.json
├── vite.config.ts
├── tsconfig.json
└── tailwind.config.js
```

### 6.3 编码规范

**Java 编码规范**：
- 遵循阿里巴巴 Java 开发手册
- 命名规范：类名 UpperCamelCase，方法名/变量名 lowerCamelCase，常量 UPPER_SNAKE_CASE
- 接口层使用 DTO/VO 隔离，禁止直接暴露实体
- 使用 Optional 避免空指针
- 日志使用 SLF4J + Logback，禁止 System.out.println

**Vue3 编码规范**：
- 使用 Composition API + `<script setup>` 语法
- 组件名使用 PascalCase
- 使用 TypeScript 严格模式
- Props 定义使用接口类型
- 状态管理使用 Pinia，避免滥用全局状态

**API 设计规范**：
- RESTful 风格，使用 HTTP 动词表达操作
- URL 使用小写字母，单词间用连字符分隔
- 请求参数校验使用 JSR-303 注解
- 接口版本控制：URL 路径中包含版本号（/api/v1/）

---

## 七、部署与运维方案

### 7.1 开发环境部署

**本地开发环境**：
- JDK 17+
- Node.js 18+
- MySQL 8.0
- Redis 7.x
- Qdrant（Docker 运行）
- MinIO（Docker 运行）

**Docker Compose 开发环境**：

```yaml
# docker-compose.dev.yml
version: '3.8'
services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: ai_agent
    ports:
      - "3306:3306"
    volumes:
      - mysql_data:/var/lib/mysql

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

  qdrant:
    image: qdrant/qdrant:latest
    ports:
      - "6333:6333"
    volumes:
      - qdrant_data:/qdrant/storage

  minio:
    image: minio/minio:latest
    command: server /data --console-address ":9001"
    ports:
      - "9000:9000"
      - "9001:9001"
    environment:
      MINIO_ROOT_USER: minio
      MINIO_ROOT_PASSWORD: minio123
    volumes:
      - minio_data:/data

  rabbitmq:
    image: rabbitmq:3-management
    ports:
      - "5672:5672"
      - "15672:15672"

volumes:
  mysql_data:
  qdrant_data:
  minio_data:
```

### 7.2 生产环境部署

**Kubernetes 部署架构**：

```
┌─────────────────────────────────────────┐
│              Ingress Controller          │
│         (Nginx / Traefik)               │
├─────────────────────────────────────────┤
│           API Gateway Pod                │
│    (Spring Cloud Gateway × 2 replicas)   │
├─────────────────────────────────────────┤
│  ┌─────────┐ ┌─────────┐ ┌─────────┐  │
│  │user-svc │ │agent-svc│ │  kb-svc │  │
│  │  ×2     │ │  ×2     │ │  ×2     │  │
│  └─────────┘ └─────────┘ └─────────┘  │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐  │
│  │chat-svc │ │mcp-svc  │ │file-svc │  │
│  │  ×2     │ │  ×2     │ │  ×2     │  │
│  └─────────┘ └─────────┘ └─────────┘  │
├─────────────────────────────────────────┤
│  MySQL StatefulSet │ Redis Cluster      │
│  Qdrant StatefulSet│ MinIO StatefulSet  │
│  RabbitMQ Cluster  │                    │
└─────────────────────────────────────────┘
```

**Helm Chart 结构**：
```
ai-agent-helm/
├── Chart.yaml
├── values.yaml
├── values-prod.yaml
├── templates/
│   ├── _helpers.tpl
│   ├── configmap.yaml
│   ├── secret.yaml
│   ├── gateway/
│   │   ├── deployment.yaml
│   │   └── service.yaml
│   ├── services/
│   │   ├── user-service.yaml
│   │   ├── agent-service.yaml
│   │   ├── knowledge-service.yaml
│   │   ├── chat-service.yaml
│   │   ├── mcp-service.yaml
│   │   └── file-service.yaml
│   ├── ingress.yaml
│   └── hpa.yaml          # 水平自动扩缩容
```

### 7.3 CI/CD 流水线

**GitLab CI / GitHub Actions 流程**：

```yaml
# .github/workflows/ci-cd.yml
stages:
  - build
  - test
  - package
  - deploy

build:
  - 代码编译
  - 单元测试
  - 代码质量检查（SonarQube）

package:
  - Docker 镜像构建
  - 镜像推送到 Harbor/ACR

deploy:
  - Helm 部署到 K8s
  - 健康检查
  - 冒烟测试
```

### 7.4 监控与告警

**监控体系**：
- **应用监控**：Micrometer + Prometheus（JVM 指标、HTTP 请求、自定义业务指标）
- **链路追踪**：Spring Cloud Sleuth + Zipkin / Jaeger
- **日志聚合**：ELK Stack（Filebeat 收集，Elasticsearch 存储，Kibana 可视化）
- **告警通知**：Prometheus Alertmanager + 钉钉/企业微信

**关键监控指标**：
- AI 服务：Token 消耗速率、响应延迟、错误率
- 向量检索：查询延迟、召回率、索引大小
- 业务指标：活跃会话数、消息发送量、用户注册量

### 7.5 安全方案

- **HTTPS 全站加密**：Ingress 配置 TLS 证书
- **API 安全**：JWT 认证 + API 限流 + 防 CSRF
- **数据安全**：数据库敏感字段加密、文件存储加密
- **AI 安全**：输入内容审核、输出内容过滤、Prompt 注入防护
- **审计日志**：关键操作记录，支持行为追溯

---

## 八、实施步骤与里程碑

### 8.1 实施阶段划分

**第一阶段：基础设施搭建**
1. 搭建开发环境（Docker Compose）
2. 创建 Maven 多模块项目骨架
3. 配置 Spring Cloud Gateway + Nacos
4. 实现用户服务（注册、登录、JWT 认证）
5. 实现前端基础框架（Vue3 + Element Plus + 路由/状态管理）

**第二阶段：核心能力构建**
1. 集成 LangChain4j，实现大模型调用
2. 实现 Agent 配置管理（模型切换、Prompt 管理）
3. 实现基础对话功能（非流式）
4. 实现流式对话（SSE）
5. 实现对话记忆管理

**第三阶段：知识库与 RAG**
1. 集成 Qdrant 向量数据库
2. 实现文档上传与解析
3. 实现文档分块与向量化
4. 实现 RAG 检索与增强生成
5. 知识库管理界面

**第四阶段：MCP 工具集成**
1. 实现 MCP Client（HTTP/Stdio 模式）
2. 集成常用 MCP 工具（代码执行、搜索等）
3. 实现工具调用与结果处理
4. 工具管理界面

**第五阶段：完善与优化**
1. 多 Agent 协作架构
2. 性能优化（缓存、连接池、异步处理）
3. 监控与日志体系
4. 生产环境部署（K8s + Helm）
5. 文档编写与测试

### 8.2 关键依赖与风险

| 风险项 | 影响 | 缓解措施 |
|--------|------|----------|
| LangChain4j 版本迭代 | API 变动 | 锁定版本，关注官方升级指南 |
| 大模型 API 稳定性 | 服务可用性 | 支持多模型切换，实现降级策略 |
| 向量数据库性能 | 检索延迟 | 合理设计索引，监控性能指标 |
| MCP 生态碎片化 | 工具兼容性 | 封装适配层，隔离协议变化 |
| 文档解析准确性 | RAG 质量 | 多解析器对比，支持人工校对 |

---

## 九、2026 年技术趋势融入

### 9.1 MCP 协议深度集成

MCP 已成为 AI Agent 与外部工具连接的事实标准。本系统通过 LangChain4j 的 `McpToolProvider` 实现 MCP 协议集成，支持 HTTP 和 Stdio 两种传输模式，可接入数千个 MCP Server 生态工具。

### 9.2 GraphRAG 增强

在传统 RAG 基础上，引入知识图谱构建（实体抽取、关系链接），实现跨文档的精准推理，特别适用于复杂知识问答场景。

### 9.3 AgentDevOps 理念

构建针对 AI Agent 的可观测体系：记录完整的推理链路（意图识别、检索、推理、工具调用），支持行为回放和 A/B 测试，保障 AI Agent 的可控与可靠。

### 9.4 多模型适配策略

支持 OpenAI、DeepSeek、通义千问、Claude 等多模型切换，通过适配器模式隔离模型差异，实现模型降级和负载均衡。

### 9.5 混合检索技术

结合稠密向量检索（语义匹配）与稀疏向量检索（BM25 关键词匹配），通过 RRF（倒数排名融合）算法提升检索准确率和召回率。

---

## 十、参考资源

### 技术文档
- LangChain4j 官方文档：https://docs.langchain4j.dev/
- MCP 协议规范：https://modelcontextprotocol.io/
- Spring Boot 3.x 官方文档：https://spring.io/projects/spring-boot
- Vue3 官方文档：https://vuejs.org/
- Qdrant 文档：https://qdrant.tech/documentation/

### 开源项目参考
- LangChain4j 示例项目：https://github.com/langchain4j/langchain4j-examples
- Spring Cloud Gateway 示例
- Vue3 + Element Plus Admin 模板
