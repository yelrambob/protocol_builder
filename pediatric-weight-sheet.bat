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

call gradlew.bat %WIN_TRUST% run --args="'%INPUT%' --peds-weights peds-weights.html"
if errorlevel 1 (
    echo.
    echo FAILED - see the error above. Nothing was written.
    echo.
    pause
    exit /b 1
)

echo.
echo Done. Open peds-weights.html in a browser and print it.
pause
endlocal
