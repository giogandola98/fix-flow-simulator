@echo off
REM FIX Flow Simulator launcher for Windows
REM Double-click this file to start the simulator in a console window

setlocal enabledelayedexpansion

SET JAR=
FOR /F "delims=" %%f IN ('dir /b /s fix-flow-api\target\fix-flow-api-*.jar 2^>nul') DO SET JAR=%%f

IF "%JAR%"=="" (
    echo ERROR: No JAR found. Run 'mvn clean package -DskipTests' first.
    pause
    exit /b 1
)

java -jar "%JAR%"
IF ERRORLEVEL 1 (
    echo.
    echo Application exited with error.
    pause
)
