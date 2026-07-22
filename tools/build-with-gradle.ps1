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
$buildGradlePath = Join-Path $projectRoot 'build.gradle'
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

function Set-LoomPluginId {
    param([string[]] $Lines, [string] $PluginId)
    return $Lines | ForEach-Object {
        if ($_ -match "^\s*id\s+'(?:net\.fabricmc\.fabric-loom|net\.fabricmc\.fabric-loom-remap|fabric-loom)'\s+version") {
            "    id '$PluginId' version `"`${loom_version}`""
        } else {
            $_
        }
    }
}

function Convert-ToLegacySource {
    param([string] $Text, [string] $RelativePath)
    $oldInputApi = $script:LegacyMinecraftVersion -like '1.20.*' -or $script:LegacyMinecraftVersion -in @('1.21.1', '1.21.4', '1.21.5', '1.21.6', '1.21.7', '1.21.8')
    $veryOldApi = $script:LegacyMinecraftVersion -like '1.20.*'
    $oldOcclusionApi = $veryOldApi -or $script:LegacyMinecraftVersion -eq '1.21.1'
    $oldScrollApi = $script:LegacyMinecraftVersion -eq '1.20.1'
    $identifierApi = $script:LegacyMinecraftVersion -eq '1.21.11'
    $oldCameraRotationApi = $script:LegacyMinecraftVersion -ne '1.21.11'
    $oldCameraPositionApi = $veryOldApi -or $script:LegacyMinecraftVersion -in @('1.21.1', '1.21.4', '1.21.5')
    $oldMobEffectApi = $script:LegacyMinecraftVersion -in @('1.20.1', '1.20.4')

    if ($RelativePath -like '*\mixin\ModelBlockRendererMixin.java') {
        return @'
package com.zenithclient.client.mixin;

import com.zenithclient.client.XrayHooks;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class ModelBlockRendererMixin {
    @Inject(method = "getRenderShape", at = @At("HEAD"), cancellable = true)
    private void zenith$xrayRenderShape(CallbackInfoReturnable<RenderShape> cir) {
        BlockState state = (BlockState) (Object) this;
        if (XrayHooks.alpha(state, null) == 0) cir.setReturnValue(RenderShape.INVISIBLE);
    }

    @Inject(method = "skipRendering", at = @At("HEAD"), cancellable = true)
    private void zenith$xrayFaces(BlockState adjacent, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        BlockState state = (BlockState) (Object) this;
        if (XrayHooks.alpha(state, null) == 255 && XrayHooks.alpha(adjacent, null) == 0) cir.setReturnValue(false);
    }
}
'@
    }

    if ($RelativePath -like '*\mixin\ChatCommandMixin.java' -and $script:LegacyMinecraftVersion -in @('1.20.1', '1.20.4')) {
        return @'
package com.zenithclient.client.mixin;

import com.zenithclient.client.ZenithClient;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatCommandMixin {
    @Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
    private void zenith$handleDotCommand(String message, boolean addToHistory, CallbackInfoReturnable<Boolean> cir) {
        if (ZenithClient.handleChatCommand(message)) cir.setReturnValue(true);
    }
}
'@
    }

    if ($RelativePath -like '*\mixin\CameraFreecamMixin.java' -and ($script:LegacyMinecraftVersion -like '1.20.*' -or $script:LegacyMinecraftVersion -like '1.21.*')) {
        return @'
package com.zenithclient.client.mixin;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Camera.class)
public abstract class CameraFreecamMixin {
}
'@
    }

    if ($RelativePath -like '*\mixin\WebBlockNoSlowMixin.java') {
        return @'
package com.zenithclient.client.mixin;

import net.minecraft.world.level.block.WebBlock;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(WebBlock.class)
public abstract class WebBlockNoSlowMixin {
}
'@
    }

    $Text = $Text.Replace('import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;', 'import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;')
    $Text = $Text.Replace('import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;', 'import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;')
    $Text = $Text.Replace('import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;', '')
    $Text = $Text.Replace('import net.minecraft.client.gui.GuiGraphicsExtractor;', 'import net.minecraft.client.gui.GuiGraphics;')
    $Text = $Text.Replace('import net.minecraft.client.renderer.RenderPipelines;', '')
    if (-not $identifierApi) {
        $Text = $Text.Replace('import net.minecraft.resources.Identifier;', 'import net.minecraft.resources.ResourceLocation;')
    }
    $Text = $Text.Replace('import net.minecraft.world.inventory.ContainerInput;', 'import net.minecraft.world.inventory.ClickType;')
    $Text = $Text.Replace('GuiGraphicsExtractor', 'GuiGraphics')
    if (-not $identifierApi) {
        $Text = $Text.Replace('Identifier', 'ResourceLocation')
    }
    $Text = $Text.Replace('KeyMappingHelper.registerKeyMapping', 'KeyBindingHelper.registerKeyBinding')
    $Text = $Text.Replace('ContainerInput.SWAP', 'ClickType.SWAP')
    $Text = $Text.Replace('handleContainerInput', 'handleInventoryMouseClick')
    $Text = $Text.Replace('setScreenAndShow', 'setScreen')
    $Text = $Text.Replace('.text(', '.drawString(')
    if ($oldInputApi) {
        $Text = $Text.Replace('import net.minecraft.client.input.CharacterEvent;', '')
        $Text = $Text.Replace('import net.minecraft.client.input.KeyEvent;', '')
        $Text = $Text.Replace('import net.minecraft.client.input.MouseButtonEvent;', '')
        $Text = $Text.Replace('.handle()', '.getWindow()')
        $Text = $Text.Replace('BuiltInRegistries.MOB_EFFECT.wrapAsHolder(MobEffects.NIGHT_VISION.value())', 'MobEffects.NIGHT_VISION')
    }
    if ($veryOldApi) {
        $Text = $Text.Replace('import net.minecraft.client.DeltaTracker;', '')
        $Text = $Text.Replace('DeltaTracker deltaTracker', 'float tickDelta')
        $Text = $Text.Replace('ScreenSpaceVisualRenderer.render(graphics, client, deltaTracker, CONFIG, HIGHLIGHTED_BLOCKS, XRAY_OUTLINE_BLOCKS);', 'ScreenSpaceVisualRenderer.render(graphics, client, tickDelta, CONFIG, HIGHLIGHTED_BLOCKS, XRAY_OUTLINE_BLOCKS);')
        $Text = $Text.Replace('ScreenSpaceVisualRenderer.render(graphics, client, deltaTracker, CONFIG, HIGHLIGHTED_BLOCKS);', 'ScreenSpaceVisualRenderer.render(graphics, client, tickDelta, CONFIG, HIGHLIGHTED_BLOCKS);')
        $Text = $Text.Replace('float tickDelta = deltaTracker.getGameTimeDeltaPartialTick(true);', '')
    }
    if ($oldInputApi) {
        $Text = $Text.Replace('KeyMapping.Category category = KeyMapping.Category.register(ResourceLocation.fromNamespaceAndPath(MOD_ID, "general"));', 'String category = "key.categories.zenithclient";')
    }
    if ($oldCameraRotationApi) {
        $Text = $Text.Replace('camera.yRot()', 'camera.getYRot()')
        $Text = $Text.Replace('camera.xRot()', 'camera.getXRot()')
    }
    if ($oldCameraPositionApi) {
        $Text = $Text.Replace('camera.position()', 'camera.getPosition()')
    }
    if ($oldMobEffectApi) {
        $Text = $Text.Replace('BuiltInRegistries.MOB_EFFECT.wrapAsHolder(MobEffects.BLINDNESS.value())', 'MobEffects.BLINDNESS')
        $Text = $Text.Replace('BuiltInRegistries.MOB_EFFECT.wrapAsHolder(MobEffects.DARKNESS.value())', 'MobEffects.DARKNESS')
    }
    $Text = $Text.Replace('HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(MOD_ID, "status_hud"),
                ZenithClient::renderHud
        );', 'HudRenderCallback.EVENT.register(ZenithClient::renderHud);')
    $Text = $Text.Replace('HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                ResourceLocation.fromNamespaceAndPath(MOD_ID, "status_hud"),
                ZenithClient::renderHud
        );', 'HudRenderCallback.EVENT.register(ZenithClient::renderHud);')
    $Text = $Text.Replace('g.blit(RenderPipelines.GUI_TEXTURED, LOGO, left + 8, top + 6, 0, 0, 48, 48, 256, 256, 256, 256, 0xFFFFFFFF);', 'g.blit(LOGO, left + 8, top + 6, 0, 0, 48, 48, 256, 256);')
    if ($script:LegacyMinecraftVersion -like '1.20.*' -or $script:LegacyMinecraftVersion -in @('1.21.1', '1.21.4', '1.21.5')) {
        $Text = $Text.Replace('g.blit(LOGO, left + 8, top + 6, 0, 0, 48, 48, 256, 256);', 'g.fill(left + 8, top + 6, left + 56, top + 54, accent);')
    }
    if ($oldInputApi) {
        if ($veryOldApi) {
            $Text = $Text.Replace('ResourceLocation.fromNamespaceAndPath(ZenithClient.MOD_ID, "icon.png")', 'new ResourceLocation(ZenithClient.MOD_ID, "icon.png")')
        }
        $Text = $Text.Replace('public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick)', 'public boolean mouseClicked(double mouseX, double mouseY, int button)')
        $Text = $Text.Replace('event.x()', 'mouseX')
        $Text = $Text.Replace('event.y()', 'mouseY')
        $Text = $Text.Replace('event.buttonInfo().button()', 'button')
        $Text = $Text.Replace('super.mouseClicked(event, doubleClick)', 'super.mouseClicked(mouseX, mouseY, button)')
        $Text = $Text.Replace('public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY)', 'public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY)')
        $Text = $Text.Replace('super.mouseDragged(event, deltaX, deltaY)', 'super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)')
        $Text = $Text.Replace('public boolean mouseReleased(MouseButtonEvent event)', 'public boolean mouseReleased(double mouseX, double mouseY, int button)')
        $Text = $Text.Replace('super.mouseReleased(event)', 'super.mouseReleased(mouseX, mouseY, button)')
        if ($oldScrollApi) {
            $Text = $Text.Replace('public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount)', 'public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount)')
            $Text = $Text.Replace('super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)', 'super.mouseScrolled(mouseX, mouseY, verticalAmount)')
        }
        $Text = $Text.Replace('public boolean keyPressed(KeyEvent event)', 'public boolean keyPressed(int keyCode, int scanCode, int modifiers)')
        $Text = $Text.Replace('private void zenith$completeDotCommand(KeyEvent event, CallbackInfoReturnable<Boolean> cir)', 'private void zenith$completeDotCommand(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir)')
        $Text = $Text.Replace('event.key()', 'keyCode')
        $Text = $Text.Replace('super.keyPressed(event)', 'super.keyPressed(keyCode, scanCode, modifiers)')
        $Text = $Text.Replace('public boolean charTyped(CharacterEvent input)', 'public boolean charTyped(char codePoint, int modifiers)')
        $Text = $Text.Replace('input.codepoint()', 'codePoint')
        $Text = $Text.Replace('super.charTyped(input)', 'super.charTyped(codePoint, modifiers)')
        if ($oldOcclusionApi) {
            $Text = $Text.Replace('adjacent.getFaceOcclusionShape(facing.getOpposite())', 'adjacent.getFaceOcclusionShape(view, adjacentPos, facing.getOpposite())')
            $Text = $Text.Replace('adjacent.isSolidRender()', 'adjacent.isSolidRender(view, adjacentPos)')
        }
    }
    $Text = $Text.Replace('public void extractRenderState(GuiGraphics g, int mouseX, int mouseY, float delta)', 'public void render(GuiGraphics g, int mouseX, int mouseY, float delta)')
    $Text = $Text.Replace('super.extractRenderState(g, mouseX, mouseY, delta);', 'super.render(g, mouseX, mouseY, delta);')
    $Text = $Text.Replace('client.player.sendSystemMessage(message);', 'client.player.displayClientMessage(message, false);')
    return $Text
}

function Write-LegacySources {
    $sourceRoot = Join-Path $projectRoot 'src\client\java'
    $legacyRoot = Join-Path $projectRoot '.generated-legacy-client\java'
    if (Test-Path $legacyRoot) { Remove-Item $legacyRoot -Recurse -Force }
    New-Item -ItemType Directory -Force -Path $legacyRoot | Out-Null
    Get-ChildItem $sourceRoot -Recurse -File -Filter '*.java' | ForEach-Object {
        $relative = $_.FullName.Substring($sourceRoot.Length + 1)
        $targetPath = Join-Path $legacyRoot $relative
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $targetPath) | Out-Null
        $text = Get-Content -LiteralPath $_.FullName -Raw
        Set-Content -LiteralPath $targetPath -Value (Convert-ToLegacySource -Text $text -RelativePath $relative) -Encoding ASCII
    }
}

function Write-VersionBuildBat {
    param([string]$MinecraftVersion)
    $safeName = $MinecraftVersion -replace '[^0-9A-Za-z.-]', '_'
    $batPath = Join-Path $projectRoot "build_$safeName.bat"
    $bat = @"
@echo off
cd /d "%~dp0"
call build_custom.bat $MinecraftVersion
"@
    Set-Content -LiteralPath $batPath -Value $bat -Encoding ASCII
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
$originalBuildGradle = Get-Content $buildGradlePath
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

Write-Host "Build selection: $MinecraftSelection"
Write-Host "Minecraft targets: $(($targets | ForEach-Object { $_.minecraft_version }) -join ', ')"
Write-Host ''

$releasesDir = Join-Path $projectRoot 'releases'
$versionDir = Join-Path $releasesDir "v$modVersion"
$latestDir = Join-Path $releasesDir 'latest'
$minecraftVersionsDir = Join-Path $projectRoot 'minecraft_versions'
$logsDir = Join-Path $projectRoot '.multi-version-logs'

foreach ($dir in @($versionDir, $latestDir, $minecraftVersionsDir, $logsDir)) {
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
}

if ($MinecraftSelection -eq 'all') {
    Write-Host "Refreshing all jars in releases/latest."
    Get-ChildItem $latestDir -Filter '*.jar' -Force -ErrorAction SilentlyContinue | Remove-Item -Force
}

$successful = New-Object System.Collections.Generic.List[string]
$failed = New-Object System.Collections.Generic.List[string]
$latestCandidate = $null
$targetCount = @($targets).Count
$targetNumber = 0

Push-Location $projectRoot
try {
    foreach ($target in $targets) {
        $targetNumber++
        $mc = $target.minecraft_version
        Write-Host ''
        Write-Host "=== [$targetNumber/$targetCount] Building ZenithClient v$modVersion for Minecraft $mc ==="
        Write-Host "Fabric API: $($target.fabric_api_version)"
        Write-Host "Loader: $($target.loader_version)"
        Write-Host "Loom: $($target.loom_version)"
        Write-Host "Plugin: $($target.loom_plugin_id)"

        $targetProperties = $originalProperties
        $targetProperties = Set-PropertyValue -Lines $targetProperties -Name 'minecraft_version' -Value $target.minecraft_version
        $targetProperties = Set-PropertyValue -Lines $targetProperties -Name 'fabric_api_version' -Value $target.fabric_api_version
        $targetProperties = Set-PropertyValue -Lines $targetProperties -Name 'loader_version' -Value $target.loader_version
        $targetProperties = Set-PropertyValue -Lines $targetProperties -Name 'loom_version' -Value $target.loom_version
        $targetProperties = Set-PropertyValue -Lines $targetProperties -Name 'mappings_mode' -Value $target.mappings_mode
        $targetProperties = Set-PropertyValue -Lines $targetProperties -Name 'legacy_sources' -Value (($target.mappings_mode -eq 'mojang').ToString().ToLowerInvariant())
        Write-Properties -Lines $targetProperties

        $loomPluginId = if ($target.loom_plugin_id) { $target.loom_plugin_id } else { 'net.fabricmc.fabric-loom' }
        Set-Content -Path $buildGradlePath -Value (Set-LoomPluginId -Lines $originalBuildGradle -PluginId $loomPluginId) -Encoding ASCII
        if ($target.mappings_mode -eq 'mojang') {
            $script:LegacyMinecraftVersion = $mc
            Write-LegacySources
        }

        $logPath = Join-Path $logsDir "mc-$mc.log"
        Write-Host "Gradle log: $logPath"
        Write-Host "Running Gradle clean build..."
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
            $mcJarPath = Join-Path $mcVersionDir $jar.Name
            $releaseJarPath = Join-Path $releaseMcDir $jar.Name
            Copy-Item $jar.FullName $mcJarPath -Force
            Copy-Item $jar.FullName $releaseJarPath -Force
            Write-Host "Created: $mcJarPath"
            Write-Host "Release copy: $releaseJarPath"
            if ($null -eq $latestCandidate) {
                $latestCandidate = $mcJarPath
            }
        }

        Write-VersionBuildBat -MinecraftVersion $mc
        $successful.Add($mc)
        Write-Host "SUCCESS for Minecraft $mc"
    }
} finally {
    Write-Properties -Lines $originalProperties
    Set-Content -Path $buildGradlePath -Value $originalBuildGradle -Encoding ASCII
    Pop-Location
}

if ($latestCandidate) {
    Write-Host ''
    Write-Host "Refreshing releases/latest with the highest successful Minecraft target only."
    Get-ChildItem $latestDir -Filter '*.jar' -Force -ErrorAction SilentlyContinue | Remove-Item -Force
    $latestJarPath = Join-Path $latestDir (Split-Path -Leaf $latestCandidate)
    Copy-Item $latestCandidate $latestJarPath -Force
    Write-Host "Latest copy: $latestJarPath"
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

if ($failed.Count -gt 0 -and -not $ContinueOnFailure) {
    throw "One or more Minecraft versions failed: $($failed -join ', ')"
}
