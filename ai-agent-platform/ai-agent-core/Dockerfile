# 多阶段构建：使用 Maven 构建 JAR，然后使用轻量级 JRE 运行
FROM maven:3.9-eclipse-temurin-17-alpine AS builder

WORKDIR /app

# 先复制父 POM 和公共模块（利用 Docker 缓存层）
COPY ai-agent-platform/pom.xml ./ai-agent-platform/
COPY ai-agent-platform/ai-agent-common/pom.xml ./ai-agent-platform/ai-agent-common/
COPY ai-agent-platform/ai-agent-common/src ./ai-agent-platform/ai-agent-common/src

# 构建公共模块
RUN cd ai-agent-platform && mvn install -pl ai-agent-common -am -DskipTests

# 复制 ai-agent-core 模块
COPY ai-agent-platform/ai-agent-core/pom.xml ./ai-agent-platform/ai-agent-core/
COPY ai-agent-platform/ai-agent-core/src ./ai-agent-platform/ai-agent-core/src

# 构建 ai-agent-core
RUN cd ai-agent-platform && mvn package -pl ai-agent-core -am -DskipTests

# 运行时镜像：使用轻量级 JRE
FROM eclipse-temurin:17-jre-alpine

LABEL maintainer="ai-agent-team"
LABEL version="1.0.0"
LABEL description="AI Agent Core Service"

# 安装必要的工具
RUN apk add --no-cache curl

# 创建应用目录
WORKDIR /app

# 创建非 root 用户运行应用
RUN addgroup -S aiagent && adduser -S aiagent -G aiagent

# 从构建阶段复制 JAR 文件
COPY --from=builder /app/ai-agent-platform/ai-agent-core/target/ai-agent-core-*.jar app.jar

# 设置权限
RUN chown -R aiagent:aiagent /app

USER aiagent

# 暴露端口
EXPOSE 8081

# JVM 参数优化（容器环境）
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -Djava.security.egd=file:/dev/./urandom"

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8081/actuator/health || exit 1

# 启动命令
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
