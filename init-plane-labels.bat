@echo off
setlocal

rem Creates/updates plane-labels.json with one empty entry per scout plane angle
rem found (e.g. "0": ""). Leave a value blank to keep the built-in default
rem (0=AP, 90=Lateral, 180=PA, 270=Lateral); fill one in only to override it for
rem your site. Safe to re-run any time; only adds new codes.
rem
rem Usage:
rem   init-plane-labels.bat
rem       Uses the sample data checked into this repo ("protocol data").
rem   init-plane-labels.bat "C:\path\to\ProtocolData"
rem   Or drag-and-drop your ProtocolData folder onto this .bat file in Explorer.

set INPUT=%~1
if "%INPUT%"=="" set INPUT=protocol data

cd /d "%~dp0"
call gradlew.bat run --args="'%INPUT%' --init-plane-labels"

echo.
pause
endlocal
