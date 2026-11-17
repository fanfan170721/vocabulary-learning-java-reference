@echo off
setlocal
cd /d "%~dp0"
call mvn -q compile exec:java
if errorlevel 1 (
    echo The application could not start. Check that Java 11+ and Maven are installed.
    pause
    exit /b 1
)
