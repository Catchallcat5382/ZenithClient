@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"

set "SUPPORTED="
for /f "skip=1 usebackq tokens=1 delims=," %%V in ("minecraft_build_versions.csv") do (
  if not "%%V"=="" set "SUPPORTED=!SUPPORTED! %%V"
)

set "MC_SELECTION=%*"
if "%MC_SELECTION%"=="" (
  echo ========================================
  echo  ZenithClient - Build Selector
  echo ========================================
  echo.
  echo Type all to build every supported version.
  echo Or type one version / comma-list, for example:
  echo   26.2
  echo   1.20.1
  echo   26.2,1.20.1
  echo.
  echo Supported versions:%SUPPORTED%
  echo.
  set /p "MC_SELECTION=Build target [all]: "
)
if "%MC_SELECTION%"=="" set "MC_SELECTION=all"
set "MC_SELECTION=%MC_SELECTION: =%"

set "VALID_SELECTION=0"
if /i "%MC_SELECTION%"=="all" set "VALID_SELECTION=1"
if not "%VALID_SELECTION%"=="1" (
  set "VALID_SELECTION=1"
  for %%S in (%MC_SELECTION:,= %) do (
    set "FOUND_ONE=0"
    for %%V in (%SUPPORTED%) do if /i "%%S"=="%%V" set "FOUND_ONE=1"
    if "!FOUND_ONE!"=="0" set "VALID_SELECTION=0"
  )
)

if not "%VALID_SELECTION%"=="1" (
  echo.
  echo Unsupported build target "%MC_SELECTION%".
  echo Type all, one version, or a comma-list from:%SUPPORTED%
  pause
  exit /b 1
)

set "CURRENT_VERSION="
for /f "usebackq tokens=2 delims==" %%V in (`findstr /b "mod_version=" gradle.properties`) do set "CURRENT_VERSION=%%V"
set "CURRENT_VERSION=%CURRENT_VERSION:v=%"
set "CURRENT_VERSION=%CURRENT_VERSION:V=%"
if "%CURRENT_VERSION%"=="" (
  echo Could not read mod_version from gradle.properties.
  pause
  exit /b 1
)
set /a NEXT_VERSION=%CURRENT_VERSION%+1

echo.
echo Current mod version: v%CURRENT_VERSION%
set /p "BUILD_VERSION=Build as version [v%NEXT_VERSION%]: "
if "%BUILD_VERSION%"=="" set "BUILD_VERSION=%NEXT_VERSION%"
set "BUILD_VERSION=%BUILD_VERSION:v=%"
set "BUILD_VERSION=%BUILD_VERSION:V=%"

set "VALID_MOD_VERSION=1"
if "%BUILD_VERSION%"=="" set "VALID_MOD_VERSION=0"
for /f "delims=0123456789" %%A in ("%BUILD_VERSION%") do set "VALID_MOD_VERSION=0"
if "%VALID_MOD_VERSION%"=="0" (
  echo.
  echo Invalid mod version "%BUILD_VERSION%". Type a whole number like 66, or press Enter for v%NEXT_VERSION%.
  pause
  exit /b 1
)

set "ORIGINAL_VERSION=%CURRENT_VERSION%"
echo Building mod version: v%BUILD_VERSION%
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "(Get-Content -LiteralPath 'gradle.properties') -replace '^mod_version=.*','mod_version=%BUILD_VERSION%' | Set-Content -LiteralPath 'gradle.properties' -Encoding UTF8"
if errorlevel 1 (
  echo Failed to prepare gradle.properties.
  pause
  exit /b 1
)

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\build-with-gradle.ps1" "%MC_SELECTION%"
set "BUILD_RESULT=!ERRORLEVEL!"

if not "!BUILD_RESULT!"=="0" (
  powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "(Get-Content -LiteralPath 'gradle.properties') -replace '^mod_version=.*','mod_version=%ORIGINAL_VERSION%' | Set-Content -LiteralPath 'gradle.properties' -Encoding UTF8"
  echo.
  echo ========================================
  echo  ACTUAL GRADLE ERROR
  echo ========================================
  set "LATEST_LOG="
  for /f "delims=" %%L in ('dir /b /a-d /o-d ".multi-version-logs\*.log" 2^>nul') do if not defined LATEST_LOG set "LATEST_LOG=.multi-version-logs\%%L"
  if defined LATEST_LOG (
    echo Log: !LATEST_LOG!
    echo.
    powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "Get-Content -LiteralPath '!LATEST_LOG!' -Tail 140"
  ) else (
    echo No Gradle log was found.
  )
  echo.
  echo BUILD FAILED
  echo Project version restored to v%ORIGINAL_VERSION%.
  echo No successful version change was saved.
  pause
  exit /b !BUILD_RESULT!
)

echo.
echo BUILD COMPLETE
echo Built target: %MC_SELECTION%
echo Project version is now v%BUILD_VERSION%.
echo Open releases\latest for the newest mod JARs.
pause
exit /b 0
