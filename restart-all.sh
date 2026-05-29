#!/bin/bash

# ============================================================
# 工作流平台 - 一键编译启动脚本
# 功能：停止旧进程 → 编译后端 → 编译前端 → 启动前后端
# 用法：./restart-all.sh
# ============================================================

set -e

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 项目路径
PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"
SERVER_DIR="${PROJECT_ROOT}/workflow-server"
WEB_DIR="${PROJECT_ROOT}/workflow-web"
LOG_DIR="${PROJECT_ROOT}/logs"
JAR_DIR="${SERVER_DIR}/target"

# 创建日志目录
mkdir -p "${LOG_DIR}"

DATE=$(date +%Y%m%d_%H%M%S)
SERVER_LOG="${LOG_DIR}/server-${DATE}.log"
WEB_LOG="${LOG_DIR}/web-${DATE}.log"

# 端口配置
SERVER_PORT=8080
WEB_PORT=3000

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  工作流平台 - 一键编译启动${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# ============================================================
# Step 1: 停止已有进程
# ============================================================
echo -e "${YELLOW}[1/5] 停止已有进程...${NC}"

# 停止后端（根据 PID 文件）
if [ -f "${SERVER_DIR}/logs/server.pid" ]; then
    PID=$(cat "${SERVER_DIR}/logs/server.pid")
    if kill -0 "$PID" 2>/dev/null; then
        echo "  停止后端服务 (PID: $PID)..."
        kill "$PID" 2>/dev/null || true
        sleep 3
        # 如果还没停掉，再发一次 SIGTERM
        if kill -0 "$PID" 2>/dev/null; then
            kill "$PID" 2>/dev/null || true
            sleep 2
        fi
        # 最后才用 SIGKILL
        if kill -0 "$PID" 2>/dev/null; then
            echo "  后端未正常退出，强制终止..."
            kill -9 "$PID" 2>/dev/null || true
        fi
    fi
    rm -f "${SERVER_DIR}/logs/server.pid"
fi

# 停止前端（根据 PID 文件）
if [ -f "${PROJECT_ROOT}/logs/web.pid" ]; then
    PID=$(cat "${PROJECT_ROOT}/logs/web.pid")
    if kill -0 "$PID" 2>/dev/null; then
        echo "  停止前端服务 (PID: $PID)..."
        kill "$PID" 2>/dev/null || true
        sleep 2
        if kill -0 "$PID" 2>/dev/null; then
            kill -9 "$PID" 2>/dev/null || true
        fi
    fi
    rm -f "${PROJECT_ROOT}/logs/web.pid"
fi

# 兜底：精确匹配本项目启动的 Java jar 进程，避免误杀其他程序
# 注意：不再使用 lsof -ti :port 或宽泛的 pkill，防止误杀微信等客户端
JAR_NAME="workflow-server-1.0.0.jar"
JAVA_PIDS=$(pgrep -f "java.*${JAR_NAME}" 2>/dev/null || true)
if [ -n "$JAVA_PIDS" ]; then
    echo "  发现遗留 Java 进程，正在清理..."
    echo "$JAVA_PIDS" | while read -r PID; do
        if [ -n "$PID" ]; then
            kill "$PID" 2>/dev/null || true
            sleep 1
            kill -9 "$PID" 2>/dev/null || true
        fi
    done
fi

echo -e "${GREEN}  ✓ 进程清理完成${NC}"
echo ""

# ============================================================
# Step 2: 编译后端
# ============================================================
echo -e "${YELLOW}[2/5] 编译后端 (Maven)...${NC}"
cd "${SERVER_DIR}"

if ! command -v mvn &> /dev/null; then
    echo -e "${RED}  ✗ mvn 命令未找到，请确保 Maven 已安装并加入 PATH${NC}"
    exit 1
fi

# 先 clean，再打包成 jar（跳过测试）
mvn clean package -DskipTests -q

JAR_FILE="${JAR_DIR}/${JAR_NAME}"
if [ ! -f "$JAR_FILE" ]; then
    echo -e "${RED}  ✗ 后端打包失败，未找到 jar 文件: ${JAR_FILE}${NC}"
    exit 1
fi

echo -e "${GREEN}  ✓ 后端编译完成: ${JAR_NAME}${NC}"
echo ""

# ============================================================
# Step 3: 编译/安装前端依赖
# ============================================================
echo -e "${YELLOW}[3/5] 安装前端依赖 (npm)...${NC}"
cd "${WEB_DIR}"

if ! command -v npm &> /dev/null; then
    echo -e "${RED}  ✗ npm 命令未找到，请确保 Node.js 已安装并加入 PATH${NC}"
    exit 1
fi

# 检查 node_modules 是否存在，不存在则安装
if [ ! -d "node_modules" ]; then
    echo "  node_modules 不存在，执行 npm install..."
    npm install
else
    echo "  node_modules 已存在，跳过 install"
fi

echo -e "${GREEN}  ✓ 前端依赖就绪${NC}"
echo ""

# ============================================================
# Step 4: 启动后端
# ============================================================
echo -e "${YELLOW}[4/5] 启动后端服务 (端口: ${SERVER_PORT})...${NC}"
cd "${SERVER_DIR}"

mkdir -p logs
nohup java -jar "${JAR_FILE}" --server.port=${SERVER_PORT} > "${SERVER_LOG}" 2>&1 &
SERVER_PID=$!
echo $SERVER_PID > logs/server.pid

echo "  后端 PID: $SERVER_PID"
echo "  日志文件: ${SERVER_LOG}"

# 等待后端启动
echo "  等待后端启动..."
for i in {1..60}; do
    if curl -s http://localhost:${SERVER_PORT}/api/auth/current > /dev/null 2>&1; then
        echo -e "${GREEN}  ✓ 后端启动成功${NC}"
        break
    fi
    if [ $i -eq 60 ]; then
        echo -e "${RED}  ✗ 后端启动超时，请检查日志: ${SERVER_LOG}${NC}"
        tail -n 30 "${SERVER_LOG}"
        exit 1
    fi
    sleep 1
done
echo ""

# ============================================================
# Step 5: 启动前端
# ============================================================
echo -e "${YELLOW}[5/5] 启动前端服务 (端口: ${WEB_PORT})...${NC}"
cd "${WEB_DIR}"

nohup npm run dev > "${WEB_LOG}" 2>&1 &
WEB_PID=$!
echo $WEB_PID > "${PROJECT_ROOT}/logs/web.pid"

echo "  前端 PID: $WEB_PID"
echo "  日志文件: ${WEB_LOG}"

# 等待前端启动
echo "  等待前端启动..."
for i in {1..30}; do
    if curl -s http://localhost:${WEB_PORT} > /dev/null 2>&1; then
        echo -e "${GREEN}  ✓ 前端启动成功${NC}"
        break
    fi
    if [ $i -eq 30 ]; then
        echo -e "${RED}  ✗ 前端启动超时，请检查日志: ${WEB_LOG}${NC}"
        tail -n 30 "${WEB_LOG}"
        exit 1
    fi
    sleep 1
done
echo ""

# ============================================================
# 完成
# ============================================================
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  所有服务已启动！${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "${BLUE}访问地址:${NC}"
echo -e "  前端页面: ${GREEN}http://localhost:${WEB_PORT}${NC}"
echo -e "  后端API:  ${GREEN}http://localhost:${SERVER_PORT}${NC}"
echo ""
echo -e "${BLUE}日志查看:${NC}"
echo -e "  后端日志: tail -f ${SERVER_LOG}"
echo -e "  前端日志: tail -f ${WEB_LOG}"
echo ""
echo -e "${BLUE}常用命令:${NC}"
echo -e "  停止后端: ${YELLOW}kill $(cat ${SERVER_DIR}/logs/server.pid)${NC}"
echo -e "  停止前端: ${YELLOW}kill $(cat ${PROJECT_ROOT}/logs/web.pid)${NC}"
echo -e "  停止全部: ${YELLOW}./stop-all.sh${NC}"
echo ""
