$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$projectRoot = Split-Path -Parent $PSScriptRoot
$gradleVersion = '9.4.0'
$cacheDir = Join-Path $projectRoot '.gradle-bootstrap'
$gradleInstallDir = Join-Path $cacheDir "gradle-$gradleVersion"
$gradleExe = Join-Path $gradleInstallDir 'bin\gradle.bat'
$zipPath = Join-Path $cacheDir "gradle-$gradleVersion-bin.zip"

Write-Host '========================================'
Write-Host ' ZenithClient - Java 25 Build Tool'
Write-Host '========================================'
Write-Host ''

$javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
if (-not $javaCommand) {
    throw 'Java was not found. Install JDK 25 and make java.exe available in PATH.'
}

# java.exe writes its version to stderr. Capture stdout and stderr directly
# instead of relying on PowerShell's native-command error/output behavior.
$javaProcessInfo = New-Object System.Diagnostics.ProcessStartInfo
$javaProcessInfo.FileName = $javaCommand.Source
$javaProcessInfo.Arguments = '-version'
$javaProcessInfo.UseShellExecute = $false
$javaProcessInfo.CreateNoWindow = $true
$javaProcessInfo.RedirectStandardOutput = $true
$javaProcessInfo.RedirectStandardError = $true

$javaProcess = New-Object System.Diagnostics.Process
$javaProcess.StartInfo = $javaProcessInfo
if (-not $javaProcess.Start()) {
    throw 'Java was found, but java.exe could not be started.'
}

$javaStdout = $javaProcess.StandardOutput.ReadToEnd()
$javaStderr = $javaProcess.StandardError.ReadToEnd()
$javaProcess.WaitForExit()
$javaVersionText = (($javaStdout + [Environment]::NewLine + $javaStderr).Trim())

if ($javaProcess.ExitCode -ne 0) {
    throw "java.exe -version exited with code $($javaProcess.ExitCode):`n$javaVersionText"
}
if ([string]::IsNullOrWhiteSpace($javaVersionText)) {
    throw 'Java was found, but java.exe returned no version text.'
}

$versionMatch = [regex]::Match($javaVersionText, '(?im)\bversion\s+"(?<major>\d+)(?:[._-]|\")')
if (-not $versionMatch.Success) {
    throw "Could not understand the installed Java version:`n$javaVersionText"
}

$javaMajor = [int]$versionMatch.Groups['major'].Value
if ($javaMajor -ne 25) {
    throw "ZenithClient requires JDK 25. Found Java $javaMajor instead:`n$javaVersionText"
}

Write-Host $javaVersionText
Write-Host "Using: $($javaCommand.Source)"
Write-Host ''

New-Item -ItemType Directory -Force -Path $cacheDir | Out-Null
if (-not (Test-Path $gradleExe)) {
    Write-Host "Downloading Gradle $gradleVersion..."
    Invoke-WebRequest -UseBasicParsing -Uri "https://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip" -OutFile $zipPath
    Write-Host 'Extracting Gradle...'
    Expand-Archive -Path $zipPath -DestinationPath $cacheDir -Force
    Remove-Item $zipPath -Force
}

Push-Location $projectRoot
try {
    & $gradleExe clean build --no-daemon
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle exited with code $LASTEXITCODE"
    }

    $versionLine = Get-Content (Join-Path $projectRoot 'gradle.properties') | Where-Object { $_ -match '^mod_version=' } | Select-Object -First 1
    $version = ($versionLine -split '=', 2)[1].Trim()
    if ($version -notmatch '^\d+$') {
        throw "mod_version must be a whole number such as 1, 2, or 3. Found: $version"
    }

    $releaseName = "v$version"
    $releasesDir = Join-Path $projectRoot 'releases'
    $versionDir = Join-Path $releasesDir $releaseName
    $latestDir = Join-Path $releasesDir 'latest'

    foreach ($dir in @($versionDir, $latestDir)) {
        if (Test-Path $dir) {
            Get-ChildItem $dir -Force | Remove-Item -Recurse -Force
        } else {
            New-Item -ItemType Directory -Force -Path $dir | Out-Null
        }
    }

    $jars = Get-ChildItem (Join-Path $projectRoot 'build\libs') -Filter '*.jar' | Where-Object { $_.Name -notmatch '-sources\.jar$' }
    if (-not $jars) { throw 'Build completed but no release JAR was found.' }
    foreach ($jar in $jars) {
        Copy-Item $jar.FullName (Join-Path $versionDir $jar.Name) -Force
        Copy-Item $jar.FullName (Join-Path $latestDir $jar.Name) -Force
    }

    Write-Host ''
    Write-Host 'BUILD SUCCESSFUL'
    Write-Host "Version: v$version"
    Write-Host "Versioned JAR: $versionDir"
    Write-Host "Latest JAR: $latestDir"
} finally {
    Pop-Location
}
