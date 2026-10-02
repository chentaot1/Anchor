@echo off
title Anchor ADHD - Living Maritime Focus
cd /d "%~dp0"

set "EXE_PATH=desktopApp\build\compose\binaries\main\app\Anchor\Anchor.exe"

if exist "%EXE_PATH%" (
    echo Launching high-speed standalone Anchor ADHD...
    start "" "%EXE_PATH%"
) else (
    echo Starting Anchor ADHD via Gradle...
    call gradlew.bat :desktopApp:run --quiet
)
