@echo off
setlocal
cd /d "%~dp0"
where java >nul 2>&1
if errorlevel 1 (
    echo Java 21 is required. Install Java 21 and try again.
    pause
    exit /b 1
)
java -Djava.awt.headless=true -Dfile.encoding=UTF-8 -cp "bcu-pvp.jar;lib/*" online.net.PvpServerMain %*
if errorlevel 1 pause
