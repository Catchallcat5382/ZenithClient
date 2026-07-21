@echo off
setlocal EnableDelayedExpansion
cd /d "%~dp0"

where git >nul 2>nul
if errorlevel 1 (
  echo Git is not installed or is not on PATH.
  exit /b 1
)

if not exist ".git" (
  git init
  git branch -M main
)

for /f "usebackq tokens=2 delims==" %%V in (`findstr /b "mod_version=" gradle.properties`) do set CURRENT_VERSION=%%V
echo Current version: v%CURRENT_VERSION%
set /a NEXT_VERSION=%CURRENT_VERSION%+1
echo Next version: v%NEXT_VERSION%

set NEW_VERSION=%1
set FORCE_RELEASE=0
if /i "%2"=="--force" set FORCE_RELEASE=1
if /i "%1"=="--force" (
  set FORCE_RELEASE=1
  set NEW_VERSION=
)
if "%NEW_VERSION%"=="" set /p NEW_VERSION=New version, or press Enter for v%NEXT_VERSION%: 
if "%NEW_VERSION%"=="" set NEW_VERSION=%NEXT_VERSION%
set NEW_VERSION=%NEW_VERSION:v=%

if "%NEW_VERSION%"=="%CURRENT_VERSION%" (
  echo Refusing to release v%NEW_VERSION% because it is already the current version.
  echo Use the next version number, for example v%NEXT_VERSION%.
  exit /b 1
)

git rev-parse -q --verify "refs/tags/v%NEW_VERSION%" >nul 2>nul
if not errorlevel 1 (
  if not "%FORCE_RELEASE%"=="1" (
    echo Refusing to overwrite existing tag v%NEW_VERSION%.
    echo Pick a newer version, or run release_version.bat %NEW_VERSION% --force if you really mean to replace it.
    exit /b 1
  )
)

if not "%NEW_VERSION%"=="%CURRENT_VERSION%" (
  powershell -NoProfile -ExecutionPolicy Bypass -Command "(Get-Content gradle.properties) -replace '^mod_version=.*','mod_version=%NEW_VERSION%' | Set-Content -Encoding UTF8 gradle.properties"
)

call build.bat
if errorlevel 1 exit /b 1

git add .
git diff --cached --quiet
if errorlevel 1 (
  git commit -m "Release v%NEW_VERSION%"
) else (
  echo No file changes to commit for v%NEW_VERSION%.
)

if "%FORCE_RELEASE%"=="1" (
  git tag -f "v%NEW_VERSION%"
) else (
  git tag "v%NEW_VERSION%"
)

git remote get-url origin >nul 2>nul
if not errorlevel 1 (
  git push -u origin main
  if "%FORCE_RELEASE%"=="1" (
    git push -f origin "v%NEW_VERSION%"
  ) else (
    git push origin "v%NEW_VERSION%"
  )
)

where gh >nul 2>nul
if not errorlevel 1 (
  set LATEST_JAR=
  for %%J in (releases\latest\*.jar) do set LATEST_JAR=%%~fJ
  if "!LATEST_JAR!"=="" (
    echo No latest jar found in releases\latest.
    exit /b 1
  )
  gh release view "v%NEW_VERSION%" >nul 2>nul
  if errorlevel 1 (
    gh release create "v%NEW_VERSION%" "!LATEST_JAR!" --title "ZenithClient v%NEW_VERSION%" --notes "ZenithClient v%NEW_VERSION% build."
  ) else (
    if "%FORCE_RELEASE%"=="1" (
      gh release upload "v%NEW_VERSION%" "!LATEST_JAR!" --clobber
    ) else (
      echo Refusing to overwrite existing GitHub release v%NEW_VERSION%.
      exit /b 1
    )
  )
)

echo Latest version is v%NEW_VERSION%.
endlocal
