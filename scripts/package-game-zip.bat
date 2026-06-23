@echo off
cd /d "%~dp0.."
call "%~dp0build-game-zip.bat"
exit /b %errorlevel%
