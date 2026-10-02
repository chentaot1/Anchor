@echo off
setlocal enabledelayedexpansion
title Anchor ADHD - Unlock Anti-Deletion Protection
color 0E

echo ================================================================
echo           ANCHOR ADHD - ANTI-DELETION SHIELD UNLOCK
echo ================================================================
echo.
echo  WARNING: You are about to lift the deletion protection on Anchor ADHD.
echo.
echo  If you are feeling overwhelmed, distracted, or experiencing a
echo  dopamine craving, pause and take three deep breaths before proceeding.
echo.
echo  [Intentionality Guard]:
echo  A mandatory 30-second cooling-off period will begin now to prevent
echo  impulsive decisions.
echo.
echo ================================================================
echo.

echo Cooling-off timer running (30 seconds)...
timeout /t 30 /nobreak

echo.
echo ================================================================
echo  COOLING-OFF PERIOD ELAPSED.
echo ================================================================
echo.
echo  To confirm that you intentionally wish to unlock deletion permissions,
echo  type the exact phrase below (without quotes):
echo.
echo    I intentionally choose to unlock Anchor
echo.

set /p USER_INPUT="Enter phrase: "

if "%USER_INPUT%"=="I intentionally choose to unlock Anchor" (
    echo.
    echo [1/2] Removing NTFS Deny-Delete Security Controls...
    icacls "%~dp0." /remove:d Everyone /t >nul 2>&1
    
    echo [2/2] Removing lock marker...
    del /f /q "%~dp0.anchor_locked" >nul 2>&1

    echo.
    echo ================================================================
    echo     PROTECTION REMOVED: Folder permissions restored to normal.
    echo ================================================================
    echo.
) else (
    echo.
    echo [ABORTED] Phrase did not match. Anti-deletion protection remains ACTIVE.
    echo.
)

pause
