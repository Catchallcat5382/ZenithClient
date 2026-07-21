@echo off
setlocal
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\build-with-gradle.ps1" all
if errorlevel 1 (
  echo.
  echo BUILD FAILED
  echo Review the error above. ZenithClient requires JDK 25.
) else (
  echo.
  echo BUILD COMPLETE
  echo Built every supported Minecraft version.
  echo Open releases\latest for the newest mod JARs.
)
pause
