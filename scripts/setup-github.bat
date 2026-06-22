@echo off
setlocal EnableDelayedExpansion
cd /d "%~dp0.."

set "GIT="
if exist "C:\Program Files\Git\bin\git.exe" set "GIT=C:\Program Files\Git\bin\git.exe"
if not defined GIT if exist "C:\Program Files (x86)\Git\bin\git.exe" set "GIT=C:\Program Files (x86)\Git\bin\git.exe"
where git >nul 2>&1
if not defined GIT if not errorlevel 1 for /f "delims=" %%G in ('where git 2^>nul') do set "GIT=%%G"

if not defined GIT (
    echo.
    echo ========================================
    echo ERROR: Git not found
    echo ========================================
    echo Install: https://git-scm.com/download/win
    echo Check: Add Git to PATH
    echo Then run github-first-push.bat again
    echo.
    exit /b 1
)

echo Using Git: %GIT%
echo Project: %CD%
echo.
echo Remote: https://github.com/xumengmeng-0922/GunCraftAlpha.git
echo.

if not exist ".git" (
    echo Running git init ...
    "%GIT%" init
    "%GIT%" branch -M main
) else (
    echo .git already exists, skip init.
)

echo.
echo git add -A ...
"%GIT%" add -A
"%GIT%" status
echo.
set /p DO_COMMIT=Commit now? (y/N): 
if /i not "!DO_COMMIT!"=="y" (
    echo Cancelled. Run: git commit / git push
    exit /b 0
)

"%GIT%" diff --cached --quiet
if not errorlevel 1 (
    echo Nothing to commit.
) else (
    "%GIT%" commit -m "GunCraft Alpha 1.3: game, launcher, docs"
    if errorlevel 1 (
        echo.
        echo ERROR: git commit failed. Set your identity first:
        echo   git config --global user.email "you@example.com"
        echo   git config --global user.name "xumengmeng-0922"
        exit /b 1
    )
    echo Commit done.
)

"%GIT%" remote get-url origin >nul 2>&1
if errorlevel 1 (
    "%GIT%" remote add origin https://github.com/xumengmeng-0922/GunCraftAlpha.git
    echo Added remote origin.
) else (
    echo remote origin:
    "%GIT%" remote get-url origin
)

echo.
set /p DO_PUSH=Push to origin main? (y/N): 
if /i not "!DO_PUSH!"=="y" (
    echo.
    echo Next: git push -u origin main
    echo Then create Release on GitHub - see docs\GitHub发布指南.md
    exit /b 0
)

"%GIT%" push -u origin main
if errorlevel 1 (
    echo.
    echo ERROR: push failed. Log in to GitHub or check network.
    exit /b 1
)

echo.
echo Push OK. Next steps:
echo   1. Run scripts\package-game-zip.bat
echo   2. GitHub Releases: tag game-alpha-1.3 + upload zip
echo   3. See docs\GitHub发布指南.md
exit /b 0
