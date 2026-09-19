@echo off
setlocal
cd /d "%~dp0"
java -Dfile.encoding=UTF-8 -Xmx2G -jar bcu-pvp.jar
if errorlevel 1 pause
