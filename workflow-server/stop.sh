#!/bin/bash

PID_FILE="logs/server.pid"

if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    echo "Stopping Workflow Server (PID: $PID)..."
    kill $PID
    rm -f "$PID_FILE"
    echo "Server stopped."
else
    echo "PID file not found. Server may not be running."
    # 尝试查找并停止
    pkill -f "workflow-server"
    echo "Killed any running workflow-server processes."
fi
