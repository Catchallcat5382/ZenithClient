@echo off
setlocal
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\build-with-gradle.ps1"
if errorlevel 1 (
  echo.
  echo BUILD FAILED
  echo Review the error above. ZenithClient requires JDK 25.
) else (
  echo.
  echo BUILD COMPLETE
  echo Open releases\latest for the newest mod JAR.
)
pause
