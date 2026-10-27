@echo off
chcp 65001 >nul
echo ========================================
echo   师承智导 - 启动前端服务
echo ========================================
echo.

cd /d "%~dp0frontend"

echo [1/2] 检查依赖...
if not exist "node_modules" (
    echo [!!] 首次运行，安装依赖...
    call npm install
)

echo.
echo [2/2] 启动前端服务...
echo 访问地址: http://localhost:5174
echo 演示模式: http://localhost:5174?demo=true
echo.
call npm run dev
