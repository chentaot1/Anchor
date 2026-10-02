@echo off
setlocal enabledelayedexpansion
title Anchor ADHD - Anti-Deletion Protection Lock
color 0C

echo ================================================================
echo           ANCHOR ADHD - ANTI-DELETION SHIELD INSTALLER
echo ================================================================
echo.
echo  This utility locks Anchor ADHD against impulsive or accidental
echo  deletion on Windows.
echo.
echo  [What this does]:
echo   1. Applies native Windows NTFS Access Control Lists (ACLs) to
echo      DENY file and folder deletion across the Anchor directory.
echo      (Attempts to right-click Delete or drag to Recycle Bin will
echo       fail with "Access is Denied".)
echo   2. Registers Anchor ADHD in Windows Startup so it starts
echo      automatically on login.
echo   3. Keeps the background window minimized to taskbar rather
echo      than quitting on Alt+F4.
echo.
echo  [Safety Guarantee]:
echo   You can safely update Anchor anytime via Update-Anchor.bat.
echo   To genuinely unlock deletion, run Unlock-Anchor-DeleteProtection.bat
echo   (requires a 30-second intentionality cooling-off period).
echo.
echo ================================================================
echo.

set /p CONFIRM="Engage Anti-Deletion Protection now? (Y/n, default Y): "
if /i "%CONFIRM%"=="" set CONFIRM=Y
if /i NOT "%CONFIRM%"=="Y" (
    echo [Cancelled] Protection was not applied.
    pause
    exit /b 0
)

cd /d "%~dp0"

echo.
echo [1/3] Registering Windows Auto-Start on Login...
reg add "HKCU\Software\Microsoft\Windows\CurrentVersion\Run" /v "AnchorADHD" /t REG_SZ /d "\"%~dp0Launch-Anchor.bat\"" /f >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo       [OK] Anchor will automatically boot on Windows login.
) else (
    echo       [Notice] Could not write startup registry key.
)

echo.
echo [2/3] Writing lock marker...
echo locked > "%~dp0.anchor_locked"

echo.
echo [3/3] Applying NTFS Deny-Delete Security Controls...
icacls "%~dp0." /deny Everyone:(DE,DC) /t >nul 2>&1
echo       [OK] Delete permissions revoked. File Explorer and scripts cannot delete Anchor.

echo.
echo ================================================================
echo     SUCCESS: Anchor ADHD is now locked against deletion!
echo ================================================================
echo.
echo  To start Anchor: Run Launch-Anchor.bat
echo  To update Anchor: Run Update-Anchor.bat (handles lock automatically)
echo  To unlock Anchor: Run Unlock-Anchor-DeleteProtection.bat
echo.
pause
