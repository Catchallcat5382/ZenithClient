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
set /a NEXT_VERSION=%CURRENT_VERSION%+1

set SUPPORTED=
for /f "skip=1 usebackq tokens=1 delims=," %%V in ("minecraft_build_versions.csv") do (
  if not "%%V"=="" set SUPPORTED=!SUPPORTED! %%V
)

echo Current mod version: v%CURRENT_VERSION%
echo Next mod version: v%NEXT_VERSION%
echo Build targets:%SUPPORTED%

set NEW_VERSION=%1
set MC_SELECTION=%2
set FORCE_RELEASE=0
if /i "%1"=="--force" (
  set FORCE_RELEASE=1
  set NEW_VERSION=
  set MC_SELECTION=
)
if /i "%2"=="--force" (
  set FORCE_RELEASE=1
  set MC_SELECTION=
)
if /i "%3"=="--force" set FORCE_RELEASE=1

if "%NEW_VERSION%"=="" set /p NEW_VERSION=New mod version, or press Enter for v%NEXT_VERSION%: 
if "%NEW_VERSION%"=="" set NEW_VERSION=%NEXT_VERSION%
set NEW_VERSION=%NEW_VERSION:v=%

if "%NEW_VERSION%"=="%CURRENT_VERSION%" (
  echo Refusing to release v%NEW_VERSION% because it is already the current version.
  echo Use the next version number, for example v%NEXT_VERSION%.
  exit /b 1
)

if "%MC_SELECTION%"=="" set /p MC_SELECTION=Minecraft version, comma-list, or "all" [all]: 
if "%MC_SELECTION%"=="" set MC_SELECTION=all
set MC_SELECTION=%MC_SELECTION: =%

set VALID_SELECTION=0
if /i "%MC_SELECTION%"=="all" set VALID_SELECTION=1
if not "%VALID_SELECTION%"=="1" (
  set VALID_SELECTION=1
  for %%S in (%MC_SELECTION:,= %) do (
    set FOUND_ONE=0
    for %%V in (%SUPPORTED%) do (
      if /i "%%S"=="%%V" set FOUND_ONE=1
    )
    if "!FOUND_ONE!"=="0" set VALID_SELECTION=0
  )
)
if not "%VALID_SELECTION%"=="1" (
  echo Unsupported Minecraft selection "%MC_SELECTION%".
  echo Pick all, a comma-list, or one of:%SUPPORTED%
  exit /b 1
)

set RELEASE_SUFFIX=mc%MC_SELECTION%
if /i "%MC_SELECTION%"=="all" set RELEASE_SUFFIX=all
set RELEASE_SUFFIX=%RELEASE_SUFFIX:,=-mc%
set RELEASE_TAG=v%NEW_VERSION%-%RELEASE_SUFFIX%

git rev-parse -q --verify "refs/tags/%RELEASE_TAG%" >nul 2>nul
if not errorlevel 1 (
  if not "%FORCE_RELEASE%"=="1" (
    echo Refusing to overwrite existing tag %RELEASE_TAG%.
    echo Pick a newer version, or add --force if you really mean to replace it.
    exit /b 1
  )
)

powershell -NoProfile -ExecutionPolicy Bypass -Command "(Get-Content gradle.properties) -replace '^mod_version=.*','mod_version=%NEW_VERSION%' | Set-Content -Encoding UTF8 gradle.properties"

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\build-with-gradle.ps1" %MC_SELECTION%
if errorlevel 1 exit /b 1

dir /b "releases\v%NEW_VERSION%\*.jar" >nul 2>nul
if errorlevel 1 dir /s /b "releases\v%NEW_VERSION%\*.jar" >nul 2>nul
if errorlevel 1 (
  echo No release jars found in releases\v%NEW_VERSION%.
  exit /b 1
)

git add .
git diff --cached --quiet
if errorlevel 1 (
  git commit -m "Release v%NEW_VERSION% for %RELEASE_SUFFIX%"
) else (
  echo No file changes to commit for v%NEW_VERSION%.
)

if "%FORCE_RELEASE%"=="1" (
  git tag -f "%RELEASE_TAG%"
) else (
  git tag "%RELEASE_TAG%"
)

git remote get-url origin >nul 2>nul
if not errorlevel 1 (
  git push -u origin main
  if "%FORCE_RELEASE%"=="1" (
    git push -f origin "%RELEASE_TAG%"
  ) else (
    git push origin "%RELEASE_TAG%"
  )
)

where gh >nul 2>nul
if not errorlevel 1 (
  gh release view "%RELEASE_TAG%" >nul 2>nul
  if not errorlevel 1 (
    if "%FORCE_RELEASE%"=="1" (
      for /r "releases\v%NEW_VERSION%" %%J in (*.jar) do gh release upload "%RELEASE_TAG%" "%%~fJ" --clobber
    ) else (
      echo Refusing to overwrite existing GitHub release %RELEASE_TAG%.
      exit /b 1
    )
  ) else (
    set NOTES=ZenithClient v%NEW_VERSION% for %RELEASE_SUFFIX%. Only successfully compiled Minecraft jars are attached.
    set CREATED_RELEASE=0
    for /r "releases\v%NEW_VERSION%" %%J in (*.jar) do (
      if "!CREATED_RELEASE!"=="0" (
        gh release create "%RELEASE_TAG%" "%%~fJ" --title "ZenithClient v%NEW_VERSION% - %RELEASE_SUFFIX%" --notes "!NOTES!"
        set CREATED_RELEASE=1
      ) else (
        gh release upload "%RELEASE_TAG%" "%%~fJ" --clobber
      )
    )
  )
)

echo Released %RELEASE_TAG%.
endlocal
