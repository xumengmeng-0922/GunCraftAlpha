@echo off
chcp 65001 >nul
cd /d "%~dp0.."

where mvn >nul 2>&1
if errorlevel 1 (
    echo [错误] 未找到 mvn，请安装 Maven 并将 bin 加入 PATH，或用 IDE 对 game 模块执行 package。
    exit /b 1
)

echo 正在构建 game 并打 zip...
call mvn -pl game -am package -DskipTests
if errorlevel 1 exit /b 1

set "ZIP=game\target\guncraft-game-alpha-1.3.zip"
if not exist "%ZIP%" (
    echo [错误] 未生成 %ZIP%
    exit /b 1
)

if not exist "dist" mkdir dist
copy /Y "%ZIP%" "dist\guncraft-game-alpha-1.3.zip" >nul
echo.
echo 完成:
echo   %CD%\%ZIP%
echo   %CD%\dist\guncraft-game-alpha-1.3.zip
exit /b 0
