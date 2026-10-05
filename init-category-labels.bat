@echo off
setlocal

rem Creates/updates category-labels.json with one empty entry per body part found
rem (e.g. "lower Extremities": ""). Leave a value blank to keep the guessed
rem reading category (keyword-based: Neuro/Body/MSK/Other); fill one in only to
rem override it for your site (e.g. force "spine" into "Spine" instead of "Neuro").
rem Safe to re-run any time; only adds new body parts.
rem
rem Usage:
rem   init-category-labels.bat
rem       Uses the "protocol data" folder in this repo (not tracked by git -
rem       put your real exported protocol folders there).
rem   init-category-labels.bat "C:\path\to\ProtocolData"
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

call gradlew.bat run --args="'%INPUT%' --init-category-labels"
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
