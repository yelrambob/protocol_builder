@echo off
setlocal

rem Printable quick-reference of pediatric protocols (patientType contains "pediatric"),
rem with any weight-in-kg found in the protocol name annotated with its pound equivalent
rem (e.g. "CT CHEST <5KG" -> "CT CHEST <5KG (11 lb)"). Writes peds-weights.html.
rem
rem Usage:
rem   pediatric-weight-sheet.bat
rem       Uses the "protocol data" folder in this repo (not tracked by git -
rem       put your real exported protocol folders there).
rem   pediatric-weight-sheet.bat "C:\path\to\ProtocolData"
rem   Or drag-and-drop your ProtocolData folder onto this .bat file in Explorer.

set INPUT=%~1
if "%INPUT%"=="" set INPUT=protocol data

cd /d "%~dp0"
call gradlew.bat run --args="'%INPUT%' --peds-weights peds-weights.html"

echo.
echo Done. Open peds-weights.html in a browser and print it.
pause
endlocal
