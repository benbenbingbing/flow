#!/bin/bash

# 创建日志目录
mkdir -p logs

# 获取当前日期
DATE=$(date +%Y%m%d)
LOG_FILE="logs/workflow-server-${DATE}.log"

echo "Starting Workflow Server..."
echo "Log file: ${LOG_FILE}"

# 后台启动
nohup mvn spring-boot:run > ${LOG_FILE} 2>&1 &

# 保存PID
echo $! > logs/server.pid

echo "Server started with PID: $(cat logs/server.pid)"
echo ""
echo "Commands:"
echo "  View log:  tail -f ${LOG_FILE}"
echo "  Stop:      kill $(cat logs/server.pid)"
