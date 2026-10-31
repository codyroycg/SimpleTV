@echo off
REM Windows: backs up the Fire TV, then installs Simple TV. Double-click, or: setup-firestick.bat 10.0.0.252 [apk]
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup-firestick.ps1" %*
if errorlevel 1 pause
