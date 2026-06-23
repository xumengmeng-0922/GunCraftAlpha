@echo off
setlocal EnableDelayedExpansion
if /i "%~1"=="nopause" set "NOPAUSE=1"

REM Build game zip without Maven. Needs JDK 17+ (javac + jar). Downloads LWJGL from Maven Central.

set "ROOT=%~dp0.."
cd /d "%ROOT%"

set "JAVAC="
set "JAR="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javac.exe" (
    set "JAVAC=%JAVA_HOME%\bin\javac.exe"
    set "JAR=%JAVA_HOME%\bin\jar.exe"
)
if not exist "%JAVAC%" (
    where javac >nul 2>&1
    if not errorlevel 1 for /f "delims=" %%I in ('where javac 2^>nul') do (
        if not defined JP_PICK if exist "%%~dpIjar.exe" (
            set "JAVAC=%%~dpIjavac.exe"
            set "JAR=%%~dpIjar.exe"
            set "JP_PICK=1"
        )
    )
    set "JP_PICK="
)
if not exist "%JAVAC%" (
    set "JDKH="
    for /f "delims=" %%H in ('powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0find-jdk-home.ps1"') do set "JDKH=%%H"
    if defined JDKH if exist "!JDKH!\bin\javac.exe" (
        set "JAVAC=!JDKH!\bin\javac.exe"
        set "JAR=!JDKH!\bin\jar.exe"
    )
)
if not exist "%JAVAC%" (
    echo [ERR] javac not found. Install JDK 17+ from https://adoptium.net/
    if not defined NOPAUSE pause
    exit /b 1
)
echo [OK] Using: %JAVAC%

set "VER=alpha-1.4"
set "GAME_JAR=guncraft-game-%VER%.jar"
set "OUT=%ROOT%\game\target\game-build"
set "LIB=%OUT%\lib"
set "CLS=%OUT%\classes"
set "STAGE=%OUT%\zip-stage"
set "ZIP=%ROOT%\game\target\guncraft-game-%VER%.zip"
set "LW=3.3.3"
set "JOML=1.10.5"

set "J1=lwjgl-%LW%.jar"
set "J2=lwjgl-glfw-%LW%.jar"
set "J3=lwjgl-opengl-%LW%.jar"
set "J4=lwjgl-%LW%-natives-windows.jar"
set "J5=lwjgl-glfw-%LW%-natives-windows.jar"
set "J6=lwjgl-opengl-%LW%-natives-windows.jar"
set "J7=joml-%JOML%.jar"

echo.
echo [1/5] prepare...
if exist "%OUT%" rmdir /s /q "%OUT%"
mkdir "%LIB%" 2>nul
mkdir "%CLS%" 2>nul

echo [2/5] download dependencies...
call :download "https://repo1.maven.org/maven2/org/lwjgl/lwjgl/%LW%/%J1%" "%LIB%\%J1%"
if errorlevel 1 goto :fail
call :download "https://repo1.maven.org/maven2/org/lwjgl/lwjgl-glfw/%LW%/%J2%" "%LIB%\%J2%"
if errorlevel 1 goto :fail
call :download "https://repo1.maven.org/maven2/org/lwjgl/lwjgl-opengl/%LW%/%J3%" "%LIB%\%J3%"
if errorlevel 1 goto :fail
call :download "https://repo1.maven.org/maven2/org/lwjgl/lwjgl/%LW%/%J4%" "%LIB%\%J4%"
if errorlevel 1 goto :fail
call :download "https://repo1.maven.org/maven2/org/lwjgl/lwjgl-glfw/%LW%/%J5%" "%LIB%\%J5%"
if errorlevel 1 goto :fail
call :download "https://repo1.maven.org/maven2/org/lwjgl/lwjgl-opengl/%LW%/%J6%" "%LIB%\%J6%"
if errorlevel 1 goto :fail
call :download "https://repo1.maven.org/maven2/org/joml/joml/%JOML%/%J7%" "%LIB%\%J7%"
if errorlevel 1 goto :fail

echo [3/5] compile game...
set "SRC_LIST=%TEMP%\guncraft-game-sources.txt"
del /f /q "%SRC_LIST%" 2>nul
for /r "%ROOT%\game\src\main\java" %%f in (*.java) do (
    set "PF=%%f"
    set "PF=!PF:\=/!"
    >>"%SRC_LIST%" echo !PF!
)
set "CLSF=%CLS:\=/%"
set "LIBF=%LIB:\=/%"
set "OPTS=%TEMP%\guncraft-game-javac.opts"
> "%OPTS%" echo -encoding UTF-8
>>"%OPTS%" echo -source 17
>>"%OPTS%" echo -target 17
>>"%OPTS%" echo -d "!CLSF!"
>>"%OPTS%" echo -classpath "!LIBF!/%J1%;!LIBF!/%J2%;!LIBF!/%J3%;!LIBF!/%J7%"
"%JAVAC%" @"%OPTS%" @"%SRC_LIST%"
if errorlevel 1 (
    echo [ERR] javac failed
    if not defined NOPAUSE pause
    exit /b 1
)

echo [4/5] package jar...
set "MF=%OUT%\MANIFEST.MF"
> "%MF%" echo Manifest-Version: 1.0
>>"%MF%" echo Main-Class: com.guncraft.game.Main
>>"%MF%" echo Class-Path: lib/%J1% lib/%J2% lib/%J3% lib/%J4% lib/%J5% lib/%J6% lib/%J7%
>>"%MF%" echo.
"%JAR%" cfm "%OUT%\%GAME_JAR%" "%MF%" -C "%CLS%" .
if errorlevel 1 (
    echo [ERR] jar failed
    if not defined NOPAUSE pause
    exit /b 1
)

echo [5/5] zip...
if exist "%STAGE%" rmdir /s /q "%STAGE%"
mkdir "%STAGE%\lib"
copy /y "%OUT%\%GAME_JAR%" "%STAGE%\" >nul
xcopy /y /q "%LIB%\*" "%STAGE%\lib\" >nul
if not exist "%ROOT%\game\target" mkdir "%ROOT%\game\target"
if exist "%ZIP%" del /f /q "%ZIP%"
powershell -NoProfile -ExecutionPolicy Bypass -Command "Compress-Archive -Path '%STAGE%\*' -DestinationPath '%ZIP%' -Force"
if errorlevel 1 (
    echo [ERR] zip failed
    if not defined NOPAUSE pause
    exit /b 1
)
if not exist "%ROOT%\dist" mkdir "%ROOT%\dist"
copy /y "%ZIP%" "%ROOT%\dist\guncraft-game-%VER%.zip" >nul

echo.
echo ========================================================================
echo SUCCESS (no Maven needed)
echo   ZIP: %ZIP%
echo   ZIP: %ROOT%\dist\guncraft-game-%VER%.zip
echo Upload this zip to GitHub Release tag: game-%VER%
echo ========================================================================
if not defined NOPAUSE pause
exit /b 0

:fail
echo [ERR] download failed - check network
if not defined NOPAUSE pause
exit /b 1

:download
set "DURL=%~1"
set "DFILE=%~2"
echo   %DURL%
where curl >nul 2>&1
if not errorlevel 1 (
    curl.exe -L -f --connect-timeout 20 --max-time 300 -o "%DFILE%" "%DURL%"
    if not errorlevel 1 goto :dl_ok
)
powershell -NoProfile -ExecutionPolicy Bypass -Command "try { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri '%DURL%' -OutFile '%DFILE%' -UseBasicParsing -TimeoutSec 300; exit 0 } catch { exit 1 }"
if errorlevel 1 exit /b 1
:dl_ok
if not exist "%DFILE%" exit /b 1
for %%A in ("%DFILE%") do if %%~zA LSS 10000 exit /b 1
exit /b 0