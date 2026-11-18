@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo ============================================
echo  CallerSentry - publish to GitHub
echo ============================================
echo.

set "BASH="
if exist "%ProgramFiles%\Git\bin\bash.exe" set "BASH=%ProgramFiles%\Git\bin\bash.exe"
if exist "%ProgramFiles(x86)%\Git\bin\bash.exe" set "BASH=%ProgramFiles(x86)%\Git\bin\bash.exe"
if exist "%LOCALAPPDATA%\Programs\Git\bin\bash.exe" set "BASH=%LOCALAPPDATA%\Programs\Git\bin\bash.exe"

if "%BASH%"=="" (
    echo [X] Git for Windows not found.
    echo     Install it once, then reopen this window:
    echo         winget install --id Git.Git -e
    echo     Or use the GUI: GitHub Desktop ^(desktop.github.com^)
    echo.
    pause
    exit /b 1
)

where gh >nul 2>nul
if errorlevel 1 (
    echo [X] GitHub CLI not found.
    echo     Install it once, then reopen this window:
    echo         winget install --id GitHub.cli -e
    echo     ^(a new terminal is required so PATH gets refreshed^)
    echo.
    pause
    exit /b 1
)

echo [OK] Environment ready. Starting...
echo.
"%BASH%" publish.sh

echo.
echo Done. Press any key to close.
pause >nul
