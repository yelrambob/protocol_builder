@echo off
setlocal

rem Usage:
rem   run-protocol-book.bat
rem       Uses the "protocol data" folder in this repo (not tracked by git -
rem       put your real exported protocol folders there).
rem   run-protocol-book.bat "C:\path\to\ProtocolData"
rem   Or just drag-and-drop your ProtocolData folder onto this .bat file in Explorer.
rem
rem Optional second argument: an overrides file other than protocol-overrides.json.

set INPUT=%~1
if "%INPUT%"=="" set INPUT=protocol data

set OVERRIDES=%~2
if "%OVERRIDES%"=="" set OVERRIDES=protocol-overrides.json

cd /d "%~dp0"
call gradlew.bat run --args="'%INPUT%' --html book.html --overrides '%OVERRIDES%'"

echo.
echo Done. Open book.html in a browser to see the result.
pause
endlocal
