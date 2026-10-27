@echo off
chcp 65001 >nul
echo ========================================
echo   师承智导 - 启动后端服务
echo ========================================
echo.

cd /d "%~dp0后端"

echo [1/2] 检查MySQL...
netstat -ano | findstr :3306 | findstr LISTENING >nul
if %errorlevel% neq 0 (
    echo [!!] MySQL未运行，请先启动MySQL
    echo     或运行: net start MySQL84
    pause
    exit /b 1
)
echo [OK] MySQL运行中 (端口3306)

echo.
echo [2/2] 启动后端服务...
java -jar target\cryd-1.0.0.jar --server.port=8090
