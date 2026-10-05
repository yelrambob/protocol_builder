@echo off
setlocal

rem Creates/updates kernel-labels.json with one empty entry per recon kernel
rem number found (e.g. "8": ""). Fill in the "" values from the scanner console
rem (e.g. "STD", "DTL", "BN", "BN+") - there's no way to derive these names from
rem the export itself. Safe to re-run any time; only adds new codes.
rem
rem Usage:
rem   init-kernel-labels.bat
rem       Uses the "protocol data" folder in this repo (not tracked by git -
rem       put your real exported protocol folders there).
rem   init-kernel-labels.bat "C:\path\to\ProtocolData"
rem   Or drag-and-drop your ProtocolData folder onto this .bat file in Explorer.

set INPUT=%~1
if "%INPUT%"=="" set INPUT=protocol data

cd /d "%~dp0"

if not exist "%INPUT%" (
    echo ERROR: Can't find "%INPUT%" ^(looked in %CD%^).
    echo Create a "protocol data" folder next to this .bat file and copy your exported
    echo protocol folders into it, or drag-and-drop your ProtocolData folder onto this .bat file.
    echo.
    pause
    exit /b 1
)

call gradlew.bat run --args="'%INPUT%' --init-kernel-labels"
if errorlevel 1 (
    echo.
    echo FAILED - see the error above. Nothing was written.
    echo.
    pause
    exit /b 1
)

echo.
pause
endlocal
