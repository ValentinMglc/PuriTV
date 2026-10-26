@echo off
powershell -NoProfile -Command "Get-ScheduledTask -TaskName 'PuriTV*' -ErrorAction SilentlyContinue | Unregister-ScheduledTask -Confirm:$false; Write-Host 'Verification automatique supprimee.'"
pause
