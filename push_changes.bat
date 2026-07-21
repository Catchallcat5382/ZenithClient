@echo off
setlocal
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

git remote get-url origin >nul 2>nul
if errorlevel 1 (
  where gh >nul 2>nul
  if not errorlevel 1 (
    echo No git remote named origin is configured.
    set /p CREATE_REPO=Create a private GitHub repo with GitHub CLI now? [y/N] 
    if /i "%CREATE_REPO%"=="y" (
      gh repo create ZenithClient --private --source . --remote origin
    )
  )
)

git remote get-url origin >nul 2>nul
if errorlevel 1 (
  echo Paste a GitHub repo URL, for example https://github.com/YOURNAME/ZenithClient.git
  set /p REMOTE_URL=Remote URL: 
  if "%REMOTE_URL%"=="" (
    echo No remote URL provided. Local commit will still be created.
  ) else (
    git remote add origin "%REMOTE_URL%"
  )
)

set MESSAGE=%*
if "%MESSAGE%"=="" set /p MESSAGE=Commit message: 
if "%MESSAGE%"=="" set MESSAGE=Update ZenithClient

git add .
git diff --cached --quiet
if errorlevel 1 (
  git commit -m "%MESSAGE%"
) else (
  echo Nothing to commit.
)

git remote get-url origin >nul 2>nul
if not errorlevel 1 (
  git push -u origin main
)

endlocal
