@echo off
REM ============================================================================
REM   JARVIS AI — PERSONAL AI ASSISTANT (com.ankit.jarvis)
REM   Created and developed by Ankit Gothwal
REM   Copyright (c) 2026 Ankit Gothwal. All rights reserved.
REM   Master All-in-One Setup, Build & USB Installer for Intel Pentium G645 PC
REM ============================================================================
setlocal
cd /d "%~dp0"

echo ============================================================================
echo   JARVIS AI - ANKIT GOTHWAL (ALL-IN-ONE PENTIUM G645 SETUP ^& APK BUILDER)
echo ============================================================================
echo.
echo   [1] Auto-Download Android SDK Tools to C:\Android (One-Time Setup)
echo   [2] Build ^& Install APK Locally on this PC (Pinned to CPU Core #2)
echo   [3] Push Offline Vosk Speech ^& GGUF AI Models to Phone via USB
echo   [4] Exit
echo.
set /p CHOICE="Select option (1/2/3/4): "

if "%CHOICE%"=="1" goto AUTO_SDK
if "%CHOICE%"=="2" goto BUILD_LOCAL
if "%CHOICE%"=="3" goto PUSH_MODELS
exit /b 0

:AUTO_SDK
echo.
echo [+] Downloading official Android Command-Line Tools to C:\Android...
if not exist "C:\Android\cmdline-tools\latest" mkdir "C:\Android\cmdline-tools\latest"
powershell -NoProfile -ExecutionPolicy Bypass -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip' -OutFile 'C:\Android\cmdtools.zip'; Expand-Archive -Path 'C:\Android\cmdtools.zip' -DestinationPath 'C:\Android\cmdline-tools\temp' -Force; Move-Item -Path 'C:\Android\cmdline-tools\temp\cmdline-tools\*' -Destination 'C:\Android\cmdline-tools\latest\' -Force"
echo [+] Installing platform-tools, android-34, and build-tools;34.0.0...
call "C:\Android\cmdline-tools\latest\bin\sdkmanager.bat" --licenses
call "C:\Android\cmdline-tools\latest\bin\sdkmanager.bat" "platform-tools" "platforms;android-34" "build-tools;34.0.0"
echo [+] SDK Setup Complete! Proceeding to build...
goto BUILD_LOCAL

:BUILD_LOCAL
echo.
echo [+] Checking Java 17...
where java >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo [!] Java 17 is not installed yet.
    echo     Download Eclipse Temurin JDK 17 from: https://adoptium.net/temurin/releases/?version=17
    pause
    exit /b 1
)

echo [+] Building JARVIS AI Debug APK on CPU Core #2 (/AFFINITY 2, BelowNormal)...
echo     (CPU Core #1 remains 100%% free so Windows 10 never freezes!)
start "JARVIS Pentium G645 Build" /B /WAIT /BELOWNORMAL /AFFINITY 2 cmd /c "gradlew.bat assembleDebug --no-daemon --max-workers=1 -Dkotlin.compiler.execution.strategy=in-process"

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [!] Build did not complete. If Android SDK is not installed yet, run Option [1] first!
    pause
    exit /b 1
)

echo.
echo [+] Installing APK to connected Infinix Hot 10 / ASUS Zenfone Max M1...
if exist "C:\Android\platform-tools\adb.exe" (
    "C:\Android\platform-tools\adb.exe" devices
    "C:\Android\platform-tools\adb.exe" install -r "app\build\outputs\apk\debug\app-debug.apk"
) else (
    adb devices
    adb install -r "app\build\outputs\apk\debug\app-debug.apk"
)
echo.
echo [+] Done! Open "JARVIS — Personal AI Assistant" on your phone!
pause
exit /b 0

:PUSH_MODELS
echo.
echo [+] Creating offline model directories on your connected phone...
if exist "C:\Android\platform-tools\adb.exe" (
    set "ADB_CMD=C:\Android\platform-tools\adb.exe"
) else (
    set "ADB_CMD=adb"
)
"%ADB_CMD%" shell mkdir -p /sdcard/Android/data/com.ankit.jarvis/files/vosk-models/
"%ADB_CMD%" shell mkdir -p /sdcard/Android/data/com.ankit.jarvis/files/models/
if exist "PHONE_OFFLINE_MODELS_TO_COPY\vosk-models\vosk-model-small-en-in" (
    "%ADB_CMD%" push "PHONE_OFFLINE_MODELS_TO_COPY\vosk-models\vosk-model-small-en-in" /sdcard/Android/data/com.ankit.jarvis/files/vosk-models/
)
if exist "PHONE_OFFLINE_MODELS_TO_COPY\vosk-models\vosk-model-small-hi" (
    "%ADB_CMD%" push "PHONE_OFFLINE_MODELS_TO_COPY\vosk-models\vosk-model-small-hi" /sdcard/Android/data/com.ankit.jarvis/files/vosk-models/
)
for %%f in ("PHONE_OFFLINE_MODELS_TO_COPY\models\*.gguf") do (
    "%ADB_CMD%" push "%%f" /sdcard/Android/data/com.ankit.jarvis/files/models/
)
echo [+] Model transfer complete!
pause
exit /b 0
