@echo off
setlocal

rem Opens the Protocol Builder window: choose the exported protocols folder, pick
rem protocols to leave out, go through each section, then create the book.
rem Double-click this file in Explorer. Keep this window open while you work -
rem closing it closes the Protocol Builder too.

cd /d "%~dp0"

rem Trust the same certificates Windows/your browser trust. Needed on networks that
rem inspect HTTPS (hospital/corporate), otherwise the first-run downloads of Gradle
rem and its libraries fail with "PKIX path building failed".
set "WIN_TRUST=-Djavax.net.ssl.trustStoreType=Windows-ROOT"
set "GRADLE_OPTS=%GRADLE_OPTS% %WIN_TRUST%"

call gradlew.bat %WIN_TRUST% -q gui
if errorlevel 1 (
    echo.
    echo FAILED - see the error above.
    echo.
    pause
    exit /b 1
)
endlocal
