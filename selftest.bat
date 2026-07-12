@echo off
rem Runs the headless self-test (no GUI): checks file codecs, validation and
rem the 10-minute runway scheduler against the specification's test data.
cd /d "%~dp0"
java -cp out app.SelfTest
