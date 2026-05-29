#!/bin/bash

# ============================================================
# 工作流平台 - 一键停止脚本
# 功能：停止前后端所有进程
# 用法：./stop-all.sh
# ============================================================

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"
SERVER_DIR="${PROJECT_ROOT}/workflow-server"
JAR_NAME="workflow-server-1.0.0.jar"

echo -e "${YELLOW}停止工作流平台服务...${NC}"

# 停止后端（PID文件）
if [ -f "${SERVER_DIR}/logs/server.pid" ]; then
    PID=$(cat "${SERVER_DIR}/logs/server.pid")
    if kill -0 "$PID" 2>/dev/null; then
        echo "  停止后端 (PID: $PID)..."
        kill "$PID" 2>/dev/null || true
        sleep 2
        if kill -0 "$PID" 2>/dev/null; then
            kill -9 "$PID" 2>/dev/null || true
        fi
    fi
    rm -f "${SERVER_DIR}/logs/server.pid"
fi

# 停止前端（PID文件）
if [ -f "${PROJECT_ROOT}/logs/web.pid" ]; then
    PID=$(cat "${PROJECT_ROOT}/logs/web.pid")
    if kill -0 "$PID" 2>/dev/null; then
        echo "  停止前端 (PID: $PID)..."
        kill "$PID" 2>/dev/null || true
        sleep 1
        if kill -0 "$PID" 2>/dev/null; then
            kill -9 "$PID" 2>/dev/null || true
        fi
    fi
    rm -f "${PROJECT_ROOT}/logs/web.pid"
fi

# 兜底：只精确匹配本项目的 Java jar 进程
JAVA_PIDS=$(pgrep -f "java.*${JAR_NAME}" 2>/dev/null || true)
if [ -n "$JAVA_PIDS" ]; then
    echo "  清理遗留 Java 进程..."
    echo "$JAVA_PIDS" | while read -r PID; do
        if [ -n "$PID" ]; then
            kill -9 "$PID" 2>/dev/null || true
        fi
    done
fi

# 清理 vite 开发服务器（通过精确匹配避免误杀）
VITE_PIDS=$(pgrep -f "vite" 2>/dev/null || true)
if [ -n "$VITE_PIDS" ]; then
    # 进一步过滤，只杀掉 workflow-web 目录下的 vite 进程
    echo "$VITE_PIDS" | while read -r PID; do
        if [ -n "$PID" ]; then
            CMDLINE=$(ps -p "$PID" -o command= 2>/dev/null || true)
            if echo "$CMDLINE" | grep -q "workflow-web"; then
                echo "  停止前端 vite (PID: $PID)..."
                kill -9 "$PID" 2>/dev/null || true
            fi
        fi
    done
fi

echo -e "${GREEN}✓ 所有服务已停止${NC}"
