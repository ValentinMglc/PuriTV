@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\installer.ps1" %*
pause
