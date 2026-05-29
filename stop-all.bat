@echo off
chcp 65001 >nul

:: ============================================================
:: 工作流平台 - 一键停止脚本 (Windows)
:: 用法：双击运行 stop-all.bat
:: ============================================================

echo 停止工作流平台服务...

:: 停止后端（端口 8080）
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8080" ^| findstr "LISTENING"') do (
    echo   停止后端进程 (PID: %%a)
    taskkill /F /PID %%a >nul 2>&1
)

:: 停止前端（端口 3000）
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":3000" ^| findstr "LISTENING"') do (
    echo   停止前端进程 (PID: %%a)
    taskkill /F /PID %%a >nul 2>&1
)

echo ✓ 所有服务已停止
pause
