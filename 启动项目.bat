@echo off
title CRYD Start
echo.
echo ========================================
echo    CRYD - One Click Start
echo ========================================
echo.
powershell -ExecutionPolicy Bypass -NoProfile -File "%~dp0start.ps1"
pause
