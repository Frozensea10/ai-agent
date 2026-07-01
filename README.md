# AI Agent 智能助手系统 - 开发环境配置

## 环境依赖

- JDK 17+
- Node.js 18+
- Docker & Docker Compose
- Maven 3.9+
- （可选）OpenAI / DeepSeek / 通义千问 API Key，用于大模型对话

## 快速启动

### 1. 启动基础设施服务

```bash
docker-compose up -d
```

这将启动以下服务：

| 服务 | 端口 | 说明 |
|------|------|------|
| MySQL | 3306 | 关系数据库 |
| Redis | 6379 | 缓存/会话 |
| Qdrant | 6333/6334 | 向量数据库 |
| MinIO | 9000/9001 | 对象存储 |
| Nacos | 8848/9848 | 服务注册与配置中心 |
| RabbitMQ | 5672/15672 | 消息队列 |

### 2. 服务访问地址

- Nacos 控制台: http://localhost:8848/nacos (默认账号密码: nacos/nacos)
- MinIO 控制台: http://localhost:9001 (账号: minio / 密码: minio123)
- RabbitMQ 管理界面: http://localhost:15672 (账号: admin / 密码: admin123)
- Qdrant API: http://localhost:6333

### 3. 数据库初始化

MySQL 初始化时会自动创建 `ai_agent` 数据库和必要的表。首次启动 Nacos 前，需要手动创建 nacos 数据库：

```bash
docker exec -it ai-agent-mysql mysql -uroot -proot123 -e "CREATE DATABASE IF NOT EXISTS nacos CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

### 4. 启动后端服务

在 `ai-agent-platform` 目录下，逐个启动微服务：

```bash
cd ai-agent-platform

# 启动顺序：gateway -> user -> core -> chat -> knowledge -> mcp -> file
mvn -pl ai-agent-gateway spring-boot:run -D"spring-boot.run.profiles=dev"
mvn -pl ai-agent-user spring-boot:run -D"spring-boot.run.profiles=dev"
mvn -pl ai-agent-core spring-boot:run -D"spring-boot.run.profiles=dev"
mvn -pl ai-agent-chat spring-boot:run -D"spring-boot.run.profiles=dev"
mvn -pl ai-agent-knowledge spring-boot:run -D"spring-boot.run.profiles=dev"
mvn -pl ai-agent-mcp spring-boot:run -D"spring-boot.run.profiles=dev"
mvn -pl ai-agent-file spring-boot:run -D"spring-boot.run.profiles=dev"
```

或使用 PowerShell 一次性启动多个（每个在独立窗口）：

```powershell
$cmds = @(
  'mvn -pl ai-agent-gateway spring-boot:run -D"spring-boot.run.profiles=dev"',
  'mvn -pl ai-agent-user spring-boot:run -D"spring-boot.run.profiles=dev"',
  'mvn -pl ai-agent-core spring-boot:run -D"spring-boot.run.profiles=dev"',
  'mvn -pl ai-agent-chat spring-boot:run -D"spring-boot.run.profiles=dev"',
  'mvn -pl ai-agent-knowledge spring-boot:run -D"spring-boot.run.profiles=dev"',
  'mvn -pl ai-agent-mcp spring-boot:run -D"spring-boot.run.profiles=dev"',
  'mvn -pl ai-agent-file spring-boot:run -D"spring-boot.run.profiles=dev"'
)
foreach ($cmd in $cmds) { Start-Process powershell -ArgumentList "-NoExit", "-Command", $cmd }
```

### 5. 启动前端

```bash
cd ai-agent-web
npm install
npm run dev
```

前端默认运行在 http://localhost:3000，通过 Vite 代理访问后端网关 http://localhost:8080。

### 6. 演示账号

系统初始化时会插入默认角色和权限。首次使用请注册账号，或调用接口注册测试账号：

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"Test123456","email":"test@example.com"}'
```

### 7. 停止服务

```bash
docker-compose down
```

带数据卷清除：

```bash
docker-compose down -v
```

## 后端服务端口

| 服务 | 端口 | 说明 |
|------|------|------|
| ai-agent-gateway | 8080 | API 网关 |
| ai-agent-user | 8081 | 用户认证服务 |
| ai-agent-core | 8082 | Agent 核心服务 |
| ai-agent-chat | 8083 | 对话服务 |
| ai-agent-knowledge | 8084 | 知识库服务 |
| ai-agent-file | 8085 | 文件服务 |
| ai-agent-mcp | 8086 | MCP 工具服务 |

## 注意事项

- 大模型对话需要在 `ai-agent-core/src/main/resources/application.yml` 中配置对应提供商的 API Key。
- 如果修改了数据库初始化脚本，需要删除 Docker 数据卷后重新启动：`docker-compose down -v && docker-compose up -d`。
- 前端代码变更会自动热更新；后端代码变更需要重启对应服务。
