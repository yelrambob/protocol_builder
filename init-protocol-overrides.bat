@echo off
setlocal

rem Creates/updates protocol-overrides.json with one empty entry per protocol
rem found, ready for you to fill in scanning notes or set "excluded": true.
rem Safe to re-run any time (e.g. after new protocols show up on the scanner) -
rem it only adds new protocol numbers and never touches existing notes.
rem
rem Usage:
rem   init-protocol-overrides.bat
rem       Uses the "protocol data" folder in this repo (not tracked by git -
rem       put your real exported protocol folders there).
rem   init-protocol-overrides.bat "C:\path\to\ProtocolData"
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

call gradlew.bat run --args="'%INPUT%' --init-overrides"
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
