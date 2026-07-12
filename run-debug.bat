@echo off
rem Starts the application with verbose debug logging (timer resets, takeoffs,
rem landings, every dialog shown...). Useful at the defense.
cd /d "%~dp0"
java -Dapp.debug=true -cp out app.Main
