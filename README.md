# AI Agent 智能助手系统 - 开发环境配置

## 环境依赖

- JDK 17+
- Node.js 18+
- Docker & Docker Compose
- Maven 3.9+

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

MySQL 初始化时会自动创建 `ai_agent` 数据库。首次启动 Nacos 前，需要手动创建 nacos 数据库：

```bash
docker exec -it ai-agent-mysql mysql -uroot -proot123 -e "CREATE DATABASE IF NOT EXISTS nacos CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

### 4. 停止服务

```bash
docker-compose down
```

带数据卷清除：

```bash
docker-compose down -v
```
