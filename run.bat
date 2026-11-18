@echo off
chcp 65001 >nul
cd /d "%~dp0"
if not exist out mkdir out
javac -encoding UTF-8 -d out src\TimeIsMoneyApp.java
if errorlevel 1 (
  echo.
  echo Compilation failed. Check that JDK 17 or newer is installed.
  pause
  exit /b 1
)
java -cp out TimeIsMoneyApp
