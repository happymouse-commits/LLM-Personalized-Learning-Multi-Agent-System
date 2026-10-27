@echo off
title CRYD Stop
setlocal enabledelayedexpansion
echo.
echo Stopping CRYD services (ports 8090, 5174)...
echo.

for %%p in (8090 5173 5174) do (
    for /f "tokens=5" %%i in ('netstat -ano ^| findstr /c:":%%p "') do (
        echo   Killing PID %%i (port %%p)
        taskkill /PID %%i /F >nul 2>&1
    )
)

echo.
echo All CRYD services stopped!
echo.
pause
