@echo off
setlocal
cd /d "%~dp0"
if "%~1"=="" (
  echo Usage: build_custom.bat 26.2
  echo Usage: build_custom.bat 1.20.1
  echo Usage: build_custom.bat 26.2,1.20.1
  echo.
  echo Plain build.bat is the recommended all-version build.
  pause
  exit /b 1
)
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\build-with-gradle.ps1" %*
if errorlevel 1 (
  echo.
  echo CUSTOM BUILD FAILED
  echo Review the error above. ZenithClient requires JDK 25.
) else (
  echo.
  echo CUSTOM BUILD COMPLETE
  echo Open releases\latest for the newest selected mod JARs.
)
pause
