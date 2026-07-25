@echo off
setlocal

rem Creates/updates detector-labels.json with one empty entry per detector row
rem count found (e.g. "128": ""). Fill in the "" values from the scanner console
rem (e.g. "64 slice", "128 slice/80mm") - there's no way to derive these names
rem from the export itself. Safe to re-run any time; only adds new codes.
rem
rem Usage:
rem   init-detector-labels.bat
rem       Uses the "protocol data" folder in this repo (not tracked by git -
rem       put your real exported protocol folders there).
rem   init-detector-labels.bat "C:\path\to\ProtocolData"
rem   Or drag-and-drop your ProtocolData folder onto this .bat file in Explorer.

set INPUT=%~1
if "%INPUT%"=="" set INPUT=protocol data

cd /d "%~dp0"
call gradlew.bat run --args="'%INPUT%' --init-detector-labels"

echo.
pause
endlocal
