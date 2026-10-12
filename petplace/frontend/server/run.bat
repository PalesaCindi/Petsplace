@echo off
rem Compiles and starts the PetPlace server. Double-click this file, or run it from this folder.
cd /d "%~dp0"
where javac >nul 2>nul
if errorlevel 1 (
  echo Java is not installed or not on your PATH. Install a JDK ^(version 17 or newer^) and try again.
  pause
  exit /b 1
)
if not exist out mkdir out
javac -encoding UTF-8 -d out src\*.java
if errorlevel 1 (
  echo.
  echo The code did not compile. Copy the messages above and send them to me.
  pause
  exit /b 1
)
java -cp out Main
pause
