param(
    [string] $MinecraftSelection = 'all',
    [switch] $ContinueOnFailure
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$projectRoot = Split-Path -Parent $PSScriptRoot
$gradleVersion = '9.4.0'
$cacheDir = Join-Path $projectRoot '.gradle-bootstrap'
$gradleInstallDir = Join-Path $cacheDir "gradle-$gradleVersion"
$gradleExe = Join-Path $gradleInstallDir 'bin\gradle.bat'
$zipPath = Join-Path $cacheDir "gradle-$gradleVersion-bin.zip"
$propertiesPath = Join-Path $projectRoot 'gradle.properties'
$matrixPath = Join-Path $projectRoot 'minecraft_build_versions.csv'

Write-Host '========================================'
Write-Host ' ZenithClient - Multi-Version Build Tool'
Write-Host '========================================'
Write-Host ''

function Get-PropertyValue {
    param([string[]] $Lines, [string] $Name)
    $line = $Lines | Where-Object { $_ -match "^$([regex]::Escape($Name))=" } | Select-Object -First 1
    if (-not $line) { throw "Missing $Name in gradle.properties" }
    return ($line -split '=', 2)[1].Trim()
}

function Set-PropertyValue {
    param([string[]] $Lines, [string] $Name, [string] $Value)
    $found = $false
    $updated = foreach ($line in $Lines) {
        if ($line -match "^$([regex]::Escape($Name))=") {
            $found = $true
            "$Name=$Value"
        } else {
            $line
        }
    }
    if (-not $found) { $updated += "$Name=$Value" }
    return $updated
}

function Write-Properties {
    param([string[]] $Lines)
    Set-Content -Path $propertiesPath -Value $Lines -Encoding UTF8
}

function Require-Java25 {
    $javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
    if (-not $javaCommand) {
        throw 'Java was not found. Install JDK 25 and make java.exe available in PATH.'
    }

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
}

function Ensure-Gradle {
    New-Item -ItemType Directory -Force -Path $cacheDir | Out-Null
    if (-not (Test-Path $gradleExe)) {
        Write-Host "Downloading Gradle $gradleVersion..."
        Invoke-WebRequest -UseBasicParsing -Uri "https://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip" -OutFile $zipPath
        Write-Host 'Extracting Gradle...'
        Expand-Archive -Path $zipPath -DestinationPath $cacheDir -Force
        Remove-Item $zipPath -Force
    }
}

Require-Java25
Ensure-Gradle

if (-not (Test-Path $matrixPath)) {
    throw "Missing minecraft_build_versions.csv"
}

$originalProperties = Get-Content $propertiesPath
$modVersion = Get-PropertyValue -Lines $originalProperties -Name 'mod_version'
if ($modVersion -notmatch '^\d+$') {
    throw "mod_version must be a whole number such as 28 or 29. Found: $modVersion"
}

$matrix = Import-Csv $matrixPath
if (-not $matrix) { throw 'minecraft_build_versions.csv is empty.' }

$targets = if ($MinecraftSelection -eq 'all') {
    $matrix
} else {
    $wanted = $MinecraftSelection -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ }
    $matrix | Where-Object { $wanted -contains $_.minecraft_version }
}

if (-not $targets) {
    $available = ($matrix.minecraft_version -join ', ')
    throw "No matching Minecraft build target for '$MinecraftSelection'. Available: $available"
}

$releasesDir = Join-Path $projectRoot 'releases'
$versionDir = Join-Path $releasesDir "v$modVersion"
$latestDir = Join-Path $releasesDir 'latest'
$minecraftVersionsDir = Join-Path $projectRoot 'minecraft_versions'
$logsDir = Join-Path $projectRoot '.multi-version-logs'

foreach ($dir in @($versionDir, $latestDir, $minecraftVersionsDir, $logsDir)) {
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
}

Get-ChildItem $latestDir -Filter '*.jar' -Force -ErrorAction SilentlyContinue | Remove-Item -Force

$successful = New-Object System.Collections.Generic.List[string]
$failed = New-Object System.Collections.Generic.List[string]

Push-Location $projectRoot
try {
    foreach ($target in $targets) {
        $mc = $target.minecraft_version
        Write-Host ''
        Write-Host "=== Building ZenithClient v$modVersion for Minecraft $mc ==="

        $targetProperties = $originalProperties
        $targetProperties = Set-PropertyValue -Lines $targetProperties -Name 'minecraft_version' -Value $target.minecraft_version
        $targetProperties = Set-PropertyValue -Lines $targetProperties -Name 'fabric_api_version' -Value $target.fabric_api_version
        $targetProperties = Set-PropertyValue -Lines $targetProperties -Name 'loader_version' -Value $target.loader_version
        $targetProperties = Set-PropertyValue -Lines $targetProperties -Name 'loom_version' -Value $target.loom_version
        Write-Properties -Lines $targetProperties

        $logPath = Join-Path $logsDir "mc-$mc.log"
        & cmd.exe /c "`"$gradleExe`" clean build --no-daemon > `"$logPath`" 2>&1"
        $exitCode = $LASTEXITCODE

        if ($exitCode -ne 0) {
            Write-Host "FAILED for Minecraft $mc. Log: $logPath" -ForegroundColor Yellow
            $failed.Add($mc)
            if (-not $ContinueOnFailure) {
                Write-Host "Stopping at first incompatible version so broken jars are not produced."
                break
            }
            continue
        }

        $jars = Get-ChildItem (Join-Path $projectRoot 'build\libs') -Filter '*.jar' | Where-Object { $_.Name -notmatch '-sources\.jar$' }
        if (-not $jars) {
            Write-Host "FAILED for Minecraft ${mc}: build passed but no release jar was found." -ForegroundColor Yellow
            $failed.Add($mc)
            if (-not $ContinueOnFailure) { break }
            continue
        }

        $mcVersionDir = Join-Path $minecraftVersionsDir $mc
        $releaseMcDir = Join-Path $versionDir $mc
        foreach ($dir in @($mcVersionDir, $releaseMcDir)) {
            New-Item -ItemType Directory -Force -Path $dir | Out-Null
            Get-ChildItem $dir -Filter '*.jar' -Force -ErrorAction SilentlyContinue | Remove-Item -Force
        }

        foreach ($jar in $jars) {
            Copy-Item $jar.FullName (Join-Path $mcVersionDir $jar.Name) -Force
            Copy-Item $jar.FullName (Join-Path $releaseMcDir $jar.Name) -Force
            Copy-Item $jar.FullName (Join-Path $latestDir $jar.Name) -Force
        }

        $successful.Add($mc)
        Write-Host "SUCCESS for Minecraft $mc"
    }
} finally {
    Write-Properties -Lines $originalProperties
    Pop-Location
}

Write-Host ''
Write-Host 'BUILD SUMMARY'
Write-Host "Mod version: v$modVersion"
Write-Host "Successful Minecraft versions: $($successful -join ', ')"
if ($failed.Count -gt 0) {
    Write-Host "Stopped/failed at: $($failed -join ', ')" -ForegroundColor Yellow
}
Write-Host "Latest jars: $latestDir"
Write-Host "Per-Minecraft jars: $minecraftVersionsDir"

if ($successful.Count -eq 0) {
    throw 'No Minecraft versions built successfully.'
}
