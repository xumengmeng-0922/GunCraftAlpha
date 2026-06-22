@echo off
chcp 65001 >nul
cd /d "%~dp0"
call "%~dp0scripts\package-game-zip.bat"
exit /b %errorlevel%
