@echo off
rem Starts the application (build.bat must have been run first).
cd /d "%~dp0"
java -cp out app.Main
