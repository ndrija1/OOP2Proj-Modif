@echo off
rem Starts the application with ALL practice modifications enabled
rem (B2 rectangle select, C1 plane info, C2 storms) + debug logging.
rem To enable a single one instead:  java -Dmod.c2=true -cp out app.Main
cd /d "%~dp0"
java -Dapp.debug=true -Dmods=all -cp out app.Main
