@echo off
setlocal

rem Creates/updates protocol-overrides.json with one empty entry per protocol
rem found, ready for you to fill in scanning notes or set "excluded": true.
rem Safe to re-run any time (e.g. after new protocols show up on the scanner) -
rem it only adds new protocol numbers and never touches existing notes.
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
