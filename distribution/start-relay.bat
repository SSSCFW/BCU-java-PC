@echo off
setlocal
cd /d "%~dp0"
if "%~1"=="" (
  java -Dfile.encoding=UTF-8 -cp "bcu-pvp.jar;lib/*" online.net.RoomServer 127.0.0.1 8766
) else (
  java -Dfile.encoding=UTF-8 -cp "bcu-pvp.jar;lib/*" online.net.RoomServer %*
)
if errorlevel 1 pause
