@echo off
setlocal

rem Creates/updates kernel-labels.json with one empty entry per recon kernel
rem number found (e.g. "8": ""). Fill in the "" values from the scanner console
rem (e.g. "STD", "DTL", "BN", "BN+") - there's no way to derive these names from
rem the export itself. Safe to re-run any time; only adds new codes.
rem
rem Usage:
rem   init-kernel-labels.bat
rem       Uses the sample data checked into this repo ("protocol data").
rem   init-kernel-labels.bat "C:\path\to\ProtocolData"
rem   Or drag-and-drop your ProtocolData folder onto this .bat file in Explorer.

set INPUT=%~1
if "%INPUT%"=="" set INPUT=protocol data

cd /d "%~dp0"
call gradlew.bat run --args="'%INPUT%' --init-kernel-labels"

echo.
pause
endlocal
