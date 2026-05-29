#!/bin/bash

SERVER_DIR="$(cd "$(dirname "$0")" && pwd)"
PID_FILE="${SERVER_DIR}/logs/server.pid"
JAR_NAME="workflow-server-1.0.0.jar"

if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if kill -0 "$PID" 2>/dev/null; then
        echo "Stopping Workflow Server (PID: $PID)..."
        kill "$PID" 2>/dev/null || true
        sleep 2
        if kill -0 "$PID" 2>/dev/null; then
            echo "Force killing Workflow Server (PID: $PID)..."
            kill -9 "$PID" 2>/dev/null || true
        fi
        echo "Server stopped."
    else
        echo "PID $PID is not running."
    fi
    rm -f "$PID_FILE"
else
    echo "PID file not found. Trying to find running server process..."
    JAVA_PIDS=$(pgrep -f "java.*${JAR_NAME}" 2>/dev/null || true)
    if [ -n "$JAVA_PIDS" ]; then
        echo "Found server processes: $JAVA_PIDS"
        echo "$JAVA_PIDS" | while read -r PID; do
            if [ -n "$PID" ]; then
                kill -9 "$PID" 2>/dev/null || true
            fi
        done
        echo "Server stopped."
    else
        echo "No running server process found."
    fi
fi
