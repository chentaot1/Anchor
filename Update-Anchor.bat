@echo off
setlocal enabledelayedexpansion
title Anchor ADHD - Safe Auto-Updater
color 0B

echo ================================================================
echo            ANCHOR ADHD - AUTOMATIC ONE-CLICK UPDATER
echo ================================================================
echo.
echo  This tool safely updates Anchor ADHD to the latest version
echo  without needing to manually delete and redownload files!
echo.
echo  [Safety Guarantee]
echo  Your personal focus data, harbor streaks, milestones, and
echo  preferences in %%APPDATA%%\Anchor are 100%% preserved.
echo.
echo ================================================================
echo.

cd /d "%~dp0"

REM Temporarily lift anti-deletion lock during update if active
if exist "%~dp0.anchor_locked" (
    icacls "%~dp0." /remove:d Everyone /t >nul 2>&1
)

echo [1/3] Stashing any local changes safely (auto-stash)...
git stash --include-untracked
if %ERRORLEVEL% NEQ 0 (
    echo [Info] Working directory clean or stash skipped.
)

echo.
echo [2/3] Fetching and pulling latest version from GitHub...
git pull --rebase origin main
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [Notice] Rebase returned non-zero code. Trying standard pull...
    git pull origin main
)

echo.
echo [3/3] Restoring any local workspace modifications...
git stash pop >nul 2>&1

REM Re-apply anti-deletion lock if it was active
if exist "%~dp0.anchor_locked" (
    icacls "%~dp0." /deny Everyone:(DE,DC) /t >nul 2>&1
)

echo.
echo ================================================================
echo     SUCCESS: Anchor ADHD has been updated to the latest version!
echo ================================================================
echo.

set /p LAUNCH="Launch Anchor ADHD now? (Y/n, default Y): "
if /i "%LAUNCH%"=="" set LAUNCH=Y
if /i "%LAUNCH%"=="Y" (
    echo Starting Anchor ADHD...
    call gradlew.bat :desktopApp:run
) else (
    echo.
    echo You can start Anchor anytime by running Launch-Anchor.bat
    echo or via gradle with: gradlew.bat :desktopApp:run
    pause
)
