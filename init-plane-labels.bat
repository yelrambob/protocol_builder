@echo off
setlocal

rem Creates/updates plane-labels.json with one empty entry per scout plane angle
rem found (e.g. "0": ""). Leave a value blank to keep the built-in default
rem (0=AP, 90=Lateral, 180=PA, 270=Lateral); fill one in only to override it for
rem your site. Safe to re-run any time; only adds new codes.
rem
rem Usage:
rem   init-plane-labels.bat
rem       Uses the "protocol data" folder in this repo (not tracked by git -
rem       put your real exported protocol folders there).
rem   init-plane-labels.bat "C:\path\to\ProtocolData"
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

rem Trust the same certificates Windows/your browser trust. Needed on networks that
rem inspect HTTPS (hospital/corporate), otherwise the first-run downloads of Gradle
rem and its libraries fail with "PKIX path building failed".
set "WIN_TRUST=-Djavax.net.ssl.trustStoreType=Windows-ROOT"
set "GRADLE_OPTS=%GRADLE_OPTS% %WIN_TRUST%"

call gradlew.bat %WIN_TRUST% run --args="'%INPUT%' --init-plane-labels"
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
