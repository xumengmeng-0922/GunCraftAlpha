@echo off
cd /d "%~dp0"
call "%~dp0scripts\build-game-zip.bat"
exit /b %errorlevel%
