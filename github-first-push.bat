@echo off
cd /d "%~dp0"

set "GIT="
if exist "C:\Program Files\Git\bin\git.exe" set "GIT=C:\Program Files\Git\bin\git.exe"
if not defined GIT if exist "C:\Program Files (x86)\Git\bin\git.exe" set "GIT=C:\Program Files (x86)\Git\bin\git.exe"
where git >nul 2>&1
if not defined GIT if not errorlevel 1 for /f "delims=" %%G in ('where git 2^>nul') do set "GIT=%%G"

if not defined GIT goto NOGIT

call :SETUP_IDENTITY
if errorlevel 1 goto END_FAIL

set "GITEMAIL="
set "GITNAME="
for /f "delims=" %%E in ('"%GIT%" config --global user.email 2^>nul') do set "GITEMAIL=%%E"
for /f "delims=" %%N in ('"%GIT%" config --global user.name 2^>nul') do set "GITNAME=%%N"

echo Using Git: %GIT%
echo Git user: %GITNAME% ^<%GITEMAIL%^>
echo Project: %CD%
echo Remote: https://github.com/xumengmeng-0922/GunCraftAlpha.git
echo.

if not exist ".git" (
  "%GIT%" init
  "%GIT%" branch -M main
)

"%GIT%" add -A
"%GIT%" status
echo.
set "DO_COMMIT="
set /p DO_COMMIT=Commit now? type y and Enter: 
if /i not "%DO_COMMIT%"=="y" goto ASK_PUSH

"%GIT%" diff --cached --quiet
if errorlevel 1 (
  "%GIT%" commit -m "GunCraft Alpha 1.3: game, launcher, docs"
  if errorlevel 1 goto COMMIT_FAIL
  echo Commit OK.
) else (
  echo Nothing new to commit.
)

:ASK_PUSH
echo.
set "DO_PUSH="
set /p DO_PUSH=Push to GitHub main? type y and Enter: 
if /i not "%DO_PUSH%"=="y" goto DONE_MANUAL

"%GIT%" remote get-url origin >nul 2>&1
if errorlevel 1 "%GIT%" remote add origin https://github.com/xumengmeng-0922/GunCraftAlpha.git

"%GIT%" push -u origin main
if errorlevel 1 goto PUSH_FAIL
echo.
echo Push OK. Open in browser:
echo https://github.com/xumengmeng-0922/GunCraftAlpha
goto END_OK

:SETUP_IDENTITY
set "GITEMAIL="
set "GITNAME="
for /f "delims=" %%E in ('"%GIT%" config --global user.email 2^>nul') do set "GITEMAIL=%%E"
for /f "delims=" %%N in ('"%GIT%" config --global user.name 2^>nul') do set "GITNAME=%%N"
if not "%GITEMAIL%"=="" if not "%GITNAME%"=="" exit /b 0

echo.
echo ========================================
echo First-time Git setup (only once)
echo ========================================
if not "%GITEMAIL%"=="" goto SET_NAME_ONLY
echo Type ONLY your email, then press Enter.
echo Example: xumengmeng0922@139.com
echo Do NOT type "git config" - this script does it for you.
echo.
set "INPUT_EMAIL="
set /p INPUT_EMAIL=Your GitHub email: 
if "%INPUT_EMAIL%"=="" (
  echo ERROR: email is empty.
  exit /b 1
)
"%GIT%" config --global user.email "%INPUT_EMAIL%"

:SET_NAME_ONLY
if not "%GITNAME%"=="" goto IDENTITY_DONE
"%GIT%" config --global user.name "xumengmeng-0922"

:IDENTITY_DONE
echo Git identity saved.
exit /b 0

:NOGIT
echo.
echo ERROR: Git is not installed.
echo Download: https://git-scm.com/download/win
goto END_FAIL

:COMMIT_FAIL
echo.
echo ERROR: commit failed.
goto END_FAIL

:PUSH_FAIL
echo.
echo ERROR: push failed. Log in to GitHub in browser, then run again.
goto END_FAIL

:DONE_MANUAL
echo Skipped push. Run manually: git push -u origin main
goto END_OK

:END_OK
echo.
pause
exit /b 0

:END_FAIL
echo.
pause
exit /b 1