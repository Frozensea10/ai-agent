#!/usr/bin/env bash
set -euo pipefail

# 一键启动 AI Agent 平台本地开发环境
# 启动顺序：基础设施（docker-compose） -> 后端微服务 -> 前端 dev server

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
PLATFORM_DIR="$PROJECT_ROOT/ai-agent-platform"
WEB_DIR="$PROJECT_ROOT/ai-agent-web"

SKIP_INFRA=false
SKIP_BACKEND=false
SKIP_FRONTEND=false
STOP_MYSQL_SERVICE=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-infra) SKIP_INFRA=true; shift ;;
    --skip-backend) SKIP_BACKEND=true; shift ;;
    --skip-frontend) SKIP_FRONTEND=true; shift ;;
    --stop-mysql-service) STOP_MYSQL_SERVICE=true; shift ;;
    *) echo "未知参数: $1"; exit 1 ;;
  esac
done

INFRA_PORTS=(3306 6379 6333 9000 9001 8848 9848 5672 15672)
BACKEND_SERVICES=(
  "ai-agent-gateway:8080"
  "ai-agent-user:8081"
  "ai-agent-core:8082"
  "ai-agent-chat:8083"
  "ai-agent-knowledge:8084"
  "ai-agent-mcp:8086"
  "ai-agent-file:8085"
)

step() {
  echo ""
  echo "[== $1 ==]"
}

port_in_use() {
  local port="$1"
  if command -v ss >/dev/null 2>&1; then
    ss -tlnp 2>/dev/null | grep -q ":$port "
  elif command -v netstat >/dev/null 2>&1; then
    netstat -tlnp 2>/dev/null | grep -q ":$port "
  else
    (echo >/dev/tcp/localhost/"$port") 2>/dev/null
  fi
}

wait_for_port() {
  local port="$1"
  local timeout="${2:-90}"
  local start=$(date +%s)
  while true; do
    if port_in_use "$port"; then
      return 0
    fi
    local now=$(date +%s)
    if (( now - start >= timeout )); then
      return 1
    fi
    sleep 0.5
  done
}

wait_for_http() {
  local url="$1"
  local timeout="${2:-60}"
  local start=$(date +%s)
  while true; do
    if curl -fs "$url" >/dev/null 2>&1; then
      return 0
    fi
    local now=$(date +%s)
    if (( now - start >= timeout )); then
      return 1
    fi
    sleep 0.5
  done
}

start_backend_service() {
  local module="$1"
  local port="$2"
  printf "  启动 %s (port %s)..." "$module" "$port"
  if command -v gnome-terminal >/dev/null 2>&1; then
    gnome-terminal -- bash -c "cd '$PLATFORM_DIR' && mvn -pl $module spring-boot:run -D'spring-boot.run.profiles=dev'; exec bash" >/dev/null 2>&1
  elif command -v xterm >/dev/null 2>&1; then
    xterm -e "cd '$PLATFORM_DIR' && mvn -pl $module spring-boot:run -D'spring-boot.run.profiles=dev'; exec bash" >/dev/null 2>&1
  else
    (cd "$PLATFORM_DIR" && mvn -pl "$module" spring-boot:run -D"spring-boot.run.profiles=dev" >"$PROJECT_ROOT/logs/$module.log" 2>&1 &)
  fi

  if wait_for_port "$port" 90; then
    echo " 就绪"
  else
    echo " 超时"
    echo "错误: $module 在 90 秒内未就绪" >&2
    exit 1
  fi
}

step "检查环境变量"
ENV_FILE="$PROJECT_ROOT/.env"
if [[ -f "$ENV_FILE" ]]; then
  echo "从 $ENV_FILE 加载环境变量..."
  set -a
  # shellcheck source=/dev/null
  source "$ENV_FILE"
  set +a
else
  echo "警告：未找到 $ENV_FILE，请复制 .env.example 并配置 LLM API Key"
fi

step "检查端口占用"
CONFLICTS=()
for port in "${INFRA_PORTS[@]}"; do
  if port_in_use "$port"; then
    CONFLICTS+=("$port")
  fi
done
for svc in "${BACKEND_SERVICES[@]}"; do
  port="${svc##*:}"
  if port_in_use "$port"; then
    CONFLICTS+=("$port")
  fi
done
if port_in_use 3000; then
  CONFLICTS+=(3000)
fi

if [[ ${#CONFLICTS[@]} -gt 0 ]]; then
  echo "以下端口已被占用：${CONFLICTS[*]}"
  if [[ " ${CONFLICTS[*]} " =~ " 3306 " ]]; then
    echo "检测到 3306 被占用。如为本地 MySQL，可使用 --stop-mysql-service 自动停止。"
    if $STOP_MYSQL_SERVICE; then
      echo "正在停止本地 MySQL 服务..."
      sudo systemctl stop mysql 2>/dev/null || sudo service mysql stop 2>/dev/null || true
      sleep 2
      if port_in_use 3306; then
        echo "无法停止本地 MySQL 服务，请手动处理"
        exit 1
      fi
    else
      exit 1
    fi
  else
    exit 1
  fi
else
  echo "所有端口均空闲"
fi

if ! $SKIP_INFRA; then
  step "启动基础设施（Docker Compose）"
  cd "$PROJECT_ROOT"
  docker-compose up -d
  echo "等待基础设施就绪..."
  sleep 20
else
  echo "跳过基础设施启动"
fi

if ! $SKIP_BACKEND; then
  step "启动后端微服务"
  mkdir -p "$PROJECT_ROOT/logs"
  cd "$PLATFORM_DIR"
  for svc in "${BACKEND_SERVICES[@]}"; do
    module="${svc%%:*}"
    port="${svc##*:}"
    start_backend_service "$module" "$port"
    sleep 2
  done
else
  echo "跳过后端服务启动"
fi

if ! $SKIP_FRONTEND; then
  step "启动前端开发服务器"
  cd "$WEB_DIR"
  if [[ ! -d node_modules ]]; then
    echo "  安装 npm 依赖..."
    npm install
  fi

  if command -v gnome-terminal >/dev/null 2>&1; then
    gnome-terminal -- bash -c "cd '$WEB_DIR' && npm run dev; exec bash" >/dev/null 2>&1
  elif command -v xterm >/dev/null 2>&1; then
    xterm -e "cd '$WEB_DIR' && npm run dev; exec bash" >/dev/null 2>&1
  else
    (npm run dev >"$PROJECT_ROOT/logs/web.log" 2>&1 &)
  fi

  if wait_for_http "http://localhost:3000" 60; then
    echo "前端就绪"
  else
    echo "前端启动超时，请手动检查"
  fi
else
  echo "跳过前端启动"
fi

step "启动完成"
echo "  前端地址: http://localhost:3000"
echo "  网关地址: http://localhost:8080"
echo "  Nacos:    http://localhost:8848/nacos"
echo "  MinIO:    http://localhost:9001"
