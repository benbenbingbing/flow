@echo off
chcp 65001 >nul

:: ============================================================
:: 工作流平台 - 一键编译启动脚本 (Windows)
:: 功能：停止旧进程 → 编译后端 → 编译前端 → 启动前后端
:: 用法：双击运行 restart-all.bat
:: ============================================================

setlocal enabledelayedexpansion

:: 项目路径
set "PROJECT_ROOT=%~dp0"
set "SERVER_DIR=%PROJECT_ROOT%workflow-server"
set "WEB_DIR=%PROJECT_ROOT%workflow-web"
set "LOG_DIR=%PROJECT_ROOT%logs"

:: 端口配置
set "SERVER_PORT=8080"
set "WEB_PORT=3000"

:: 创建日志目录
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"

set "DATE=%date:~0,4%%date:~5,2%%date:~8,2%_%time:~0,2%%time:~3,2%%time:~6,2%"
set "DATE=%DATE: =0%"
set "SERVER_LOG=%LOG_DIR%\server-%DATE%.log"
set "WEB_LOG=%LOG_DIR%\web-%DATE%.log"

echo ========================================
echo   工作流平台 - 一键编译启动
echo ========================================
echo.

:: ============================================================
:: Step 1: 停止已有进程
:: ============================================================
echo [1/5] 停止已有进程...

:: 停止后端（通过端口）
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":%SERVER_PORT%" ^| findstr "LISTENING"') do (
    echo   停止后端进程 (PID: %%a)
    taskkill /F /PID %%a >nul 2>&1
)

:: 停止前端（通过端口）
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":%WEB_PORT%" ^| findstr "LISTENING"') do (
    echo   停止前端进程 (PID: %%a)
    taskkill /F /PID %%a >nul 2>&1
)

:: 兜底：停止 Java 和 Node 进程（可选，取消注释启用）
:: taskkill /F /IM java.exe >nul 2>&1
:: taskkill /F /IM node.exe >nul 2>&1

echo   ✓ 进程清理完成
echo.

:: ============================================================
:: Step 2: 编译后端
:: ============================================================
echo [2/5] 编译后端 (Maven)...
cd /d "%SERVER_DIR%"

call mvn clean compile -q
if errorlevel 1 (
    echo   ✗ 后端编译失败，请检查 Maven 是否安装
    pause
    exit /b 1
)

echo   ✓ 后端编译完成
echo.

:: ============================================================
:: Step 3: 安装前端依赖
:: ============================================================
echo [3/5] 安装前端依赖 (npm)...
cd /d "%WEB_DIR%"

if not exist "node_modules" (
    echo   node_modules 不存在，执行 npm install...
    call npm install
    if errorlevel 1 (
        echo   ✗ npm install 失败
        pause
        exit /b 1
    )
) else (
    echo   node_modules 已存在，跳过 install
)

echo   ✓ 前端依赖就绪
echo.

:: ============================================================
:: Step 4: 启动后端
:: ============================================================
echo [4/5] 启动后端服务 (端口: %SERVER_PORT%)...
cd /d "%SERVER_DIR%"

if not exist "logs" mkdir "logs"

:: 使用 start 命令在新窗口启动后端，方便查看日志
echo   后端将在新窗口启动...
start "Workflow Server" cmd /c "mvn spring-boot:run ^> %SERVER_LOG% 2^>^&1"

echo   等待后端启动...
:WAIT_SERVER
ping -n 2 127.0.0.1 >nul
curl -s http://localhost:%SERVER_PORT%/api/auth/current >nul 2>&1
if errorlevel 1 (
    goto WAIT_SERVER
)
echo   ✓ 后端启动成功
echo.

:: ============================================================
:: Step 5: 启动前端
:: ============================================================
echo [5/5] 启动前端服务 (端口: %WEB_PORT%)...
cd /d "%WEB_DIR%"

start "Workflow Web" cmd /c "npm run dev ^> %WEB_LOG% 2^>^&1"

echo   等待前端启动...
:WAIT_WEB
ping -n 2 127.0.0.1 >nul
curl -s http://localhost:%WEB_PORT% >nul 2>&1
if errorlevel 1 (
    goto WAIT_WEB
)
echo   ✓ 前端启动成功
echo.

:: ============================================================
:: 完成
:: ============================================================
echo ========================================
echo   所有服务已启动！
echo ========================================
echo.
echo 访问地址:
echo   前端页面: http://localhost:%WEB_PORT%
echo   后端API:  http://localhost:%SERVER_PORT%
echo.
echo 日志文件:
echo   后端日志: %SERVER_LOG%
echo   前端日志: %WEB_LOG%
echo.
pause
