@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\verifier-tv.ps1" %*
pause
