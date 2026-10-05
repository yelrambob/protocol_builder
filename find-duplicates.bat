@echo off
setlocal

rem Lists protocols that are effectively duplicates - identical settings filed under two
rem numbers, or the same name with different settings (and what differs) - plus the
rem protocol-overrides.json lines that would hide the extra copies from the book.
rem Writes duplicates.html. Nothing on the scanner is changed.
rem
rem Usage:
rem   find-duplicates.bat
rem       Uses the "protocol data" folder in this repo (not tracked by git -
rem       put your real exported protocol folders there).
rem   find-duplicates.bat "C:\path\to\ProtocolData"
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

call gradlew.bat %WIN_TRUST% run --args="'%INPUT%' --duplicates duplicates.html"
if errorlevel 1 (
    echo.
    echo FAILED - see the error above. Nothing was written.
    echo.
    pause
    exit /b 1
)

echo.
echo Done. Open duplicates.html in a browser.
pause
endlocal
