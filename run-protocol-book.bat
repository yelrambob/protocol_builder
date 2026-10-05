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

call gradlew.bat %WIN_TRUST% run --args="'%INPUT%' --html book.html --overrides '%OVERRIDES%'"
if errorlevel 1 (
    echo.
    echo FAILED - see the error above. Nothing was written.
    echo.
    pause
    exit /b 1
)

echo.
echo Done. Open book.html in a browser to see the result.
pause
endlocal
