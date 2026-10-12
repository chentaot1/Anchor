@echo off
setlocal
title Anchor ADHD - Source Updater
cd /d "%~dp0"

echo Updating Anchor source and checking the Windows build.
echo Personal app data is stored separately in %%APPDATA%%\Anchor.
echo Local commits and workspace changes will not be stashed or rebased.
echo.

REM Folder protection is managed by the explicit unlock tool.
if exist "%~dp0.anchor_locked" (
    echo Update stopped: this folder has deletion protection enabled.
    echo Run Unlock-Anchor-DeleteProtection.bat, then run this updater again.
    goto failed
)

git symbolic-ref --quiet --short HEAD >nul 2>&1
if errorlevel 1 (
    echo Update stopped: open a local Git branch before updating.
    goto failed
)

echo [1/2] Downloading source updates from the current branch's upstream...
git pull --ff-only
if errorlevel 1 (
    echo.
    echo Source update failed. Check your connection, upstream branch, and local changes.
    echo No fallback pull or stash restore was attempted.
    goto failed
)

echo.
echo [2/2] Building the updated Windows app...
call gradlew.bat :desktopApp:classes --console=plain
if errorlevel 1 (
    echo.
    echo Source downloaded, but the Windows build failed. Review the error above.
    goto failed
)

echo.
echo Source update and Windows build succeeded.
echo Close any running Anchor instance before launching the updated source.
echo The standalone application image needs a separate rebuild to receive these changes.
echo.
set "LAUNCH="
set /p LAUNCH="Launch updated Anchor source now? (Y/n, default Y): "
if /i "%LAUNCH%"=="n" exit /b 0
call gradlew.bat :desktopApp:run --console=plain
exit /b %ERRORLEVEL%

:failed
echo.
echo Update did not complete.
pause
exit /b 1
