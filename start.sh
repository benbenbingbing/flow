#!/bin/bash

# 工作流平台一键编译启动脚本
# 在 flow/ 目录下执行

set -e

echo "========== 工作流平台重启脚本 =========="
echo ""

# 设置 Java 17
export JAVA_HOME=/Users/dawei/Library/Java/JavaVirtualMachines/temurin-17.0.11/Contents/Home
export PATH=$JAVA_HOME/bin:$PATH

# 加载环境变量
if [ -f .env ]; then
    export $(grep -v '^#' .env | xargs)
    echo "✅ 已加载 .env 环境变量"
else
    echo "⚠️  .env 文件不存在，使用默认配置"
fi

echo ""
echo "========== 1. 清理旧进程 =========="

# 杀掉旧的后端进程
if [ -f workflow-server/server.pid ]; then
    OLD_PID=$(cat workflow-server/server.pid)
    if ps -p "$OLD_PID" > /dev/null 2>&1; then
        echo "🛑 停止旧的后端进程 (PID: $OLD_PID)"
        kill "$OLD_PID"
        sleep 2
    fi
    rm -f workflow-server/server.pid
fi

# 杀掉旧的前端进程
if [ -f workflow-web/web.pid ]; then
    OLD_PID=$(cat workflow-web/web.pid)
    if ps -p "$OLD_PID" > /dev/null 2>&1; then
        echo "🛑 停止旧的前端进程 (PID: $OLD_PID)"
        kill "$OLD_PID"
        sleep 2
    fi
    rm -f workflow-web/web.pid
fi

# 兜底清理
pkill -f "workflow-server-1.0.0.jar" 2>/dev/null || true
pkill -f "vite" 2>/dev/null || true

echo "✅ 旧进程已清理"

echo ""
echo "========== 2. 编译后端 =========="
cd workflow-server
mvn clean package -DskipTests
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
nohup npm run dev > ../workflow-web/web.log 2>&1 &
echo $! > ../workflow-web/web.pid
cd ..
echo "🚀 前端已启动 (PID: $(cat workflow-web/web.pid))"

echo ""
echo "========== 等待服务就绪 =========="
sleep 5

# 检查后端状态
if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1 || grep -q "Tomcat started on port 8080" workflow-server/server.log 2>/dev/null; then
    echo "✅ 后端运行正常: http://localhost:8080"
else
    echo "⏳ 后端启动中，请稍候..."
fi

# 检查前端状态（通过端口检测）
if lsof -i :3000 > /dev/null 2>&1; then
    echo "✅ 前端运行正常: http://localhost:3000"
else
    echo "⏳ 前端启动中，请稍候..."
fi

echo ""
echo "========== 启动完成 =========="
echo "📦 后端地址: http://localhost:8080"
echo "🌐 前端地址: http://localhost:3000"
echo ""
echo "查看日志:"
echo "  tail -f workflow-server/server.log"
echo "  tail -f workflow-web/web.log"
