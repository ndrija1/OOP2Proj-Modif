@echo off
rem Compiles every source file into the out\ folder.
cd /d "%~dp0"
if not exist out mkdir out
dir /s /b src\*.java > sources.txt
javac -encoding UTF-8 -d out @sources.txt
set RESULT=%ERRORLEVEL%
del sources.txt
if %RESULT% neq 0 (
    echo BUILD FAILED
    exit /b %RESULT%
)
echo Build OK.
