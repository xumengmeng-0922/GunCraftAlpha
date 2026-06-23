@echo off
setlocal EnableDelayedExpansion
if /i "%~1"=="nopause" set "NOPAUSE=1"

REM JDK 17+ full install required. Downloads deps, javac, jpackage -> dist\

set "ROOT=%~dp0.."
cd /d "%ROOT%"

set "JAVAC="
set "JAR="
set "JPACKAGE="

REM 1) JAVA_HOME points to a JDK that has jpackage
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\jpackage.exe" (
    set "JAVAC=%JAVA_HOME%\bin\javac.exe"
    set "JAR=%JAVA_HOME%\bin\jar.exe"
    set "JPACKAGE=%JAVA_HOME%\bin\jpackage.exe"
)

REM 2) Same folder as each javac on PATH (where may list several)
if not exist "%JPACKAGE%" (
    where javac >nul 2>&1
    if not errorlevel 1 (
        for /f "delims=" %%I in ('where javac 2^>nul') do (
            if not defined JP_PICK if exist "%%~dpIjpackage.exe" (
                set "JAVAC=%%~dpIjavac.exe"
                set "JAR=%%~dpIjar.exe"
                set "JPACKAGE=%%~dpIjpackage.exe"
                set "JP_PICK=1"
            )
        )
        set "JP_PICK="
    )
)

REM 3) Ask running java for java.home (fixes JAVA_HOME=JRE or Oracle javapath)
if not exist "%JPACKAGE%" (
    set "JDKH="
    for /f "delims=" %%H in ('powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0find-jdk-home.ps1"') do set "JDKH=%%H"
    if defined JDKH if exist "!JDKH!\bin\jpackage.exe" (
        set "JAVAC=!JDKH!\bin\javac.exe"
        set "JAR=!JDKH!\bin\jar.exe"
        set "JPACKAGE=!JDKH!\bin\jpackage.exe"
    )
)

REM 4) Scan common install locations
if not exist "%JPACKAGE%" (
    for /d %%D in ("%ProgramFiles%\Eclipse Adoptium\jdk-*") do if exist "%%D\bin\jpackage.exe" (
        set "JAVAC=%%D\bin\javac.exe"
        set "JAR=%%D\bin\jar.exe"
        set "JPACKAGE=%%D\bin\jpackage.exe"
        goto :jdk_done
    )
    for /d %%D in ("%ProgramFiles%\Java\jdk-*") do if exist "%%D\bin\jpackage.exe" (
        set "JAVAC=%%D\bin\javac.exe"
        set "JAR=%%D\bin\jar.exe"
        set "JPACKAGE=%%D\bin\jpackage.exe"
        goto :jdk_done
    )
    for /d %%D in ("%ProgramFiles%\Microsoft\jdk-*") do if exist "%%D\bin\jpackage.exe" (
        set "JAVAC=%%D\bin\javac.exe"
        set "JAR=%%D\bin\jar.exe"
        set "JPACKAGE=%%D\bin\jpackage.exe"
        goto :jdk_done
    )
    for /d %%D in ("%LocalAppData%\Programs\Eclipse Adoptium\jdk-*") do if exist "%%D\bin\jpackage.exe" (
        set "JAVAC=%%D\bin\javac.exe"
        set "JAR=%%D\bin\jar.exe"
        set "JPACKAGE=%%D\bin\jpackage.exe"
        goto :jdk_done
    )
)

:jdk_done
if not exist "%JPACKAGE%" (
    echo [ERR] jpackage.exe not found.
    echo      JAVA_HOME may point to JRE only. Point JAVA_HOME to JDK root, or install Temurin JDK 17+.
    if not defined NOPAUSE pause
    exit /b 1
)
if not exist "%JAVAC%" (
    echo [ERR] javac.exe missing: %JAVAC%
    if not defined NOPAUSE pause
    exit /b 1
)
echo [OK] Using JDK: %JPACKAGE%

set "OUT=%ROOT%\launcher\target\launcher-build"
set "LIB=%OUT%\lib"
set "CLS=%OUT%\classes"
set "GSON_VER=2.10.1"
set "FLAT_VER=3.4.1"
set "GSON_JAR=gson-%GSON_VER%.jar"
set "FLAT_JAR=flatlaf-%FLAT_VER%.jar"
set "URL_GSON=https://repo1.maven.org/maven2/com/google/code/gson/gson/%GSON_VER%/%GSON_JAR%"
set "URL_FLAT=https://repo1.maven.org/maven2/com/formdev/flatlaf/%FLAT_VER%/%FLAT_JAR%"

echo.
echo [1/7] prepare...
if exist "%OUT%" rmdir /s /q "%OUT%"
mkdir "%LIB%" 2>nul
mkdir "%CLS%" 2>nul

echo [2/7] download jars...
call :download "%URL_GSON%" "%LIB%\%GSON_JAR%"
if errorlevel 1 goto :fail_dl
call :download "%URL_FLAT%" "%LIB%\%FLAT_JAR%"
if errorlevel 1 goto :fail_dl

echo [3/7] compile...
set "SRC_LIST=%TEMP%\guncraft-launcher-sources.txt"
del /f /q "%SRC_LIST%" 2>nul
for /r "%ROOT%\launcher\src\main\java" %%f in (*.java) do (
    set "PF=%%f"
    set "PF=!PF:\=/!"
    >>"%SRC_LIST%" echo !PF!
)
set "CLSF=%CLS:\=/%"
set "LIBF=%LIB:\=/%"
set "OPTS=%TEMP%\guncraft-launcher-javac.opts"
> "%OPTS%" echo -encoding UTF-8
>>"%OPTS%" echo -source 17
>>"%OPTS%" echo -target 17
>>"%OPTS%" echo -d "!CLSF!"
>>"%OPTS%" echo -classpath "!LIBF!/%GSON_JAR%;!LIBF!/%FLAT_JAR%"
"%JAVAC%" @"%OPTS%" @"%SRC_LIST%"
if errorlevel 1 (
    echo [ERR] javac failed
    if not defined NOPAUSE pause
    exit /b 1
)

if exist "%ROOT%\launcher\src\main\resources" (
    xcopy /e /y /i /q "%ROOT%\launcher\src\main\resources\*" "%CLS%\" >nul
)

echo [4/7] thin jar...
set "MF=%OUT%\MANIFEST.MF"
> "%MF%" echo Manifest-Version: 1.0
>>"%MF%" echo Class-Path: lib/%GSON_JAR% lib/%FLAT_JAR%
>>"%MF%" echo Main-Class: com.guncraft.launcher.Launcher
>>"%MF%" echo.
"%JAR%" cfm "%OUT%\guncraft-launcher.jar" "%MF%" -C "%CLS%" .
if errorlevel 1 (
    echo [ERR] jar failed
    if not defined NOPAUSE pause
    exit /b 1
)

echo [5/7] fat jar...
set "FATDIR=%OUT%\fatwork"
if exist "%FATDIR%" rmdir /s /q "%FATDIR%"
mkdir "%FATDIR%"
pushd "%FATDIR%"
"%JAR%" xf "%LIB%\%GSON_JAR%"
"%JAR%" xf "%LIB%\%FLAT_JAR%"
popd
del /f /q "%FATDIR%\META-INF\*.SF" 2>nul
del /f /q "%FATDIR%\META-INF\*.DSA" 2>nul
del /f /q "%FATDIR%\META-INF\*.RSA" 2>nul
xcopy /e /y /i /q "%CLS%\*" "%FATDIR%\" >nul
set "MFFAT=%OUT%\MANIFEST-FAT.MF"
> "%MFFAT%" echo Manifest-Version: 1.0
>>"%MFFAT%" echo Main-Class: com.guncraft.launcher.Launcher
>>"%MFFAT%" echo.
"%JAR%" cfm "%OUT%\guncraft-launcher-fat.jar" "%MFFAT%" -C "%FATDIR%" .
if errorlevel 1 (
    echo [ERR] fat jar failed
    if not defined NOPAUSE pause
    exit /b 1
)

echo [6/7] jpackage...
set "JPDEST=%ROOT%\dist"
set "JPAPP=%JPDEST%\GunCraftLauncher"
echo      Stopping old launcher if running...
taskkill /IM GunCraftLauncher.exe /F >nul 2>&1
timeout /t 1 /nobreak >nul
if exist "%JPAPP%" (
    rmdir /s /q "%JPAPP%" 2>nul
    if exist "%JPAPP%" (
        powershell -NoProfile -ExecutionPolicy Bypass -Command "Start-Sleep -Seconds 1; Remove-Item -LiteralPath '%JPAPP%' -Recurse -Force -ErrorAction SilentlyContinue"
    )
    if exist "%JPAPP%" (
        echo [ERR] Cannot remove: %JPAPP%
        echo      Close GunCraft Launcher, game, and any File Explorer window in that folder, then retry.
        echo      Or run fat jar: java -jar "%OUT%\guncraft-launcher-fat.jar"
        if not defined NOPAUSE pause
        exit /b 1
    )
)
if not exist "%JPDEST%" mkdir "%JPDEST%"
set "JPIN=%OUT%\jpackage-input"
if exist "%JPIN%" rmdir /s /q "%JPIN%"
mkdir "%JPIN%"
copy /y "%OUT%\guncraft-launcher-fat.jar" "%JPIN%\" >nul

"%JPACKAGE%" --type app-image --input "%JPIN%" --main-jar guncraft-launcher-fat.jar --main-class com.guncraft.launcher.Launcher --name GunCraftLauncher --dest "%JPDEST%" --app-version 1.4.0 --vendor GunCraft --copyright GunCraft --description "GunCraft Launcher" --java-options "-Dfile.encoding=UTF-8"
if errorlevel 1 (
    echo [ERR] jpackage failed. Try: java -jar "%OUT%\guncraft-launcher-fat.jar"
    if not defined NOPAUSE pause
    exit /b 1
)

echo [7/7] zip...
set "READ=%ROOT%\scripts\DIST-README.txt"
set "ZIP=%JPDEST%\GunCraftLauncher-Share.zip"
if not exist "%READ%" (
    echo [WARN] missing DIST-README.txt, skip zip
    goto :done
)
if exist "%ZIP%" del /f /q "%ZIP%"
set "READCN=%ROOT%\scripts\README-for-friends-CN.txt"
powershell -NoProfile -ExecutionPolicy Bypass -Command "Compress-Archive -Path @('%JPAPP%','%READ%','%READCN%') -DestinationPath '%ZIP%' -Force"
if errorlevel 1 (
    echo [WARN] zip failed, zip folder manually: %JPAPP%
) else (
    echo OK: %ZIP%
)

:done
echo.
echo ========================================================================
echo SUCCESS - paths:
echo   EXE:  %JPAPP%\GunCraftLauncher.exe
echo   ZIP:  %ZIP%
echo   TEMP: %OUT%
echo Opening folder...
echo ========================================================================
if exist "%JPAPP%\GunCraftLauncher.exe" (
    explorer.exe /select,"%JPAPP%\GunCraftLauncher.exe"
) else (
    explorer.exe "%JPDEST%"
)
echo.
if not defined NOPAUSE pause
exit /b 0

:fail_dl
echo [ERR] download failed
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
