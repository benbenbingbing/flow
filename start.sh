#!/bin/bash

# 工作流平台一键编译启动脚本

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

echo "========== 工作流平台重启脚本 =========="
echo ""

# 优先使用调用方的 Java 17，再兼容本机固定安装路径。
DEFAULT_JAVA_HOME=/Users/dawei/Library/Java/JavaVirtualMachines/temurin-17.0.11/Contents/Home
if [ -z "${JAVA_HOME:-}" ] && [ -x "$DEFAULT_JAVA_HOME/bin/java" ]; then
    export JAVA_HOME="$DEFAULT_JAVA_HOME"
fi
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    export PATH="$JAVA_HOME/bin:$PATH"
fi

if ! command -v java > /dev/null 2>&1; then
    echo "❌ 未找到 Java。请设置 JAVA_HOME 指向 JDK 17。"
    exit 1
fi

if ! command -v mvn > /dev/null 2>&1; then
    echo "❌ 未找到 Maven。请安装 Maven 或将其加入 PATH。"
    exit 1
fi

# 加载环境变量
if [ -f .env ]; then
    set -a
    # shellcheck disable=SC1091
    . ./.env
    set +a
    echo "✅ 已加载 .env 环境变量"
else
    echo "⚠️  .env 文件不存在，使用默认配置"
fi

SERVER_PORT="${SERVER_PORT:-8080}"
WEB_PORT="${WEB_PORT:-3000}"

echo ""
echo "========== 1. 清理旧进程 =========="

stop_pid_file() {
    local pid_file="$1"
    local service_name="${2:-}"
    if [ ! -f "$pid_file" ]; then
        return
    fi
    local old_pid
    old_pid="$(cat "$pid_file")"
    if kill -0 "$old_pid" > /dev/null 2>&1; then
        echo "🛑 停止旧的${service_name}进程 (PID: $old_pid)"
        kill "$old_pid"
    fi
    rm -f "$pid_file"
}

stop_orphaned_project_listener() {
    local port="$1"
    local service_name="${2:-}"
    local command_pattern="$3"
    local listeners listener command
    listeners="$(lsof -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null || true)"
    for listener in $listeners; do
        command="$(ps -p "$listener" -o command= 2>/dev/null || true)"
        if [[ "$command" == *"$command_pattern"* ]]; then
            echo "🛑 停止无 PID 文件的旧${service_name}进程 (PID: $listener)"
            kill "$listener"
        else
            echo "❌ ${service_name}端口 $port 被非本项目进程占用: $listener"
            echo "   $command"
            exit 1
        fi
    done
}

wait_for_port_release() {
    local port="$1"
    for _ in $(seq 1 10); do
        if ! lsof -tiTCP:"$port" -sTCP:LISTEN > /dev/null 2>&1; then
            return
        fi
        sleep 1
    done
    echo "❌ 端口 $port 在停止旧进程后仍未释放。"
    exit 1
}

stop_pid_file workflow-server/server.pid "后端"
stop_pid_file workflow-web/web.pid "前端"
stop_orphaned_project_listener "$SERVER_PORT" "后端" "workflow-server-1.0.0.jar"
stop_orphaned_project_listener "$WEB_PORT" "前端" "workflow-web"
wait_for_port_release "$SERVER_PORT"
wait_for_port_release "$WEB_PORT"

echo "✅ 旧进程已清理"

ensure_port_available() {
    local port="$1"
    local service_name="${2:-}"
    local listeners
    listeners="$(lsof -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null || true)"
    if [ -n "$listeners" ]; then
        echo "❌ ${service_name} 端口 $port 已被进程占用: $listeners"
        echo "   请先停止占用进程，或在 .env 中修改对应端口。"
        exit 1
    fi
}

ensure_port_available "$SERVER_PORT" "后端"
ensure_port_available "$WEB_PORT" "前端"

echo ""
echo "========== 2. 编译后端 =========="
cd workflow-server
mvn -pl workflow-app -am clean package -DskipTests
cd ..
echo "✅ 后端编译完成"

echo ""
echo "========== 3. 编译前端 =========="
cd workflow-web
npm run build
cd ..
echo "✅ 前端编译完成"

echo ""
echo "========== 4. 启动后端 =========="
nohup java -jar workflow-server/workflow-app/target/workflow-server-1.0.0.jar > workflow-server/server.log 2>&1 &
echo $! > workflow-server/server.pid
echo "🚀 后端已启动 (PID: $(cat workflow-server/server.pid))"

echo ""
echo "========== 5. 启动前端 =========="
cd workflow-web
nohup npm run dev -- --port "$WEB_PORT" --strictPort > ../workflow-web/web.log 2>&1 &
echo $! > ../workflow-web/web.pid
cd ..
echo "🚀 前端已启动 (PID: $(cat workflow-web/web.pid))"

echo ""
echo "========== 等待服务就绪 =========="
for _ in $(seq 1 30); do
    if ! kill -0 "$(cat workflow-server/server.pid)" 2>/dev/null; then
        echo "❌ 后端启动失败，最近日志如下："
        tail -n 80 workflow-server/server.log || true
        exit 1
    fi
    if grep -q "Tomcat started on port $SERVER_PORT" workflow-server/server.log 2>/dev/null; then
        break
    fi
    sleep 1
done

# 检查后端状态
if grep -q "Tomcat started on port $SERVER_PORT" workflow-server/server.log 2>/dev/null; then
    echo "✅ 后端运行正常: http://localhost:$SERVER_PORT"
else
    echo "❌ 后端未在预期时间内就绪，最近日志如下："
    tail -n 80 workflow-server/server.log || true
    exit 1
fi

# 检查前端状态（通过端口检测）
for _ in $(seq 1 20); do
    if ! kill -0 "$(cat workflow-web/web.pid)" 2>/dev/null; then
        echo "❌ 前端启动失败，最近日志如下："
        tail -n 80 workflow-web/web.log || true
        exit 1
    fi
    if lsof -tiTCP:"$WEB_PORT" -sTCP:LISTEN > /dev/null 2>&1; then
        break
    fi
    sleep 1
done

if lsof -tiTCP:"$WEB_PORT" -sTCP:LISTEN > /dev/null 2>&1; then
    echo "✅ 前端运行正常: http://localhost:$WEB_PORT"
else
    echo "❌ 前端未在预期时间内就绪，最近日志如下："
    tail -n 80 workflow-web/web.log || true
    exit 1
fi

echo ""
echo "========== 启动完成 =========="
echo "📦 后端地址: http://localhost:$SERVER_PORT"
echo "🌐 前端地址: http://localhost:$WEB_PORT"
echo ""
echo "查看日志:"
echo "  tail -f workflow-server/server.log"
echo "  tail -f workflow-web/web.log"
