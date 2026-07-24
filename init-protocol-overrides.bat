@echo off
setlocal

rem One-time setup: creates protocol-overrides.json with one empty entry per
rem protocol found, ready for you to fill in scanning notes or set "excluded": true.
rem Fails if protocol-overrides.json already exists, so it won't overwrite your notes.
rem
rem Usage:
rem   init-protocol-overrides.bat
rem       Uses the sample data checked into this repo ("protocol data").
rem   init-protocol-overrides.bat "C:\path\to\ProtocolData"
rem   Or drag-and-drop your ProtocolData folder onto this .bat file in Explorer.

set INPUT=%~1
if "%INPUT%"=="" set INPUT=protocol data

cd /d "%~dp0"
call gradlew.bat run --args="'%INPUT%' --init-overrides"

echo.
pause
endlocal
