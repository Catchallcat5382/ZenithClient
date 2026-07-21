<p align="center">
  <img src="src/main/resources/assets/zenithclient/icon.png" width="96" alt="ZenithClient logo">
</p>

# ZenithClient

ZenithClient is a client-side Fabric utility mod for Minecraft 26.2 built with JDK 25.

## Features

### Visuals

- Player ESP: exact model-hugging glow outline, tracers, name tags, color, and range.
- Entity ESP: exact model-hugging glow outline with target groups, tracers, name tags, color, and range.
- Item ESP: exact dropped-item glow outline, labels, color, range, and tracers.
- Projectile ESP: exact projectile glow outline, labels, color, range, and tracers.
- Block ESP: configurable block group, color, fill opacity, radius, and cached low-lag scanning.
- Trajectories: projectile path preview with quality, color, thickness, and start-distance controls.
- X-Ray: safe chunk-rebuild toggle that shows selected valuable blocks while hiding non-target block geometry.

### Combat

- Criticals.
- Auto Totem: refills the offhand with a totem from inventory when one is available.

### Movement

- Flight with horizontal speed, vertical speed, and sprint multiplier.
- Auto Sprint.
- No Slow.
- No Stun.
- No Fall.
- Air Jump.

### HUD

- Fullbright.
- FPS HUD.
- Coordinates HUD.

## Controls

Press Right Shift during gameplay to open ZenithClient. Module keybinds only fire while no screen is open, so typing in chat or another text screen will not toggle modules.

Right-click a module card to open its settings. Numeric settings support both dragging the slider and typing an exact value.

## Build

1. Install JDK 25.
2. Run `build.bat`.
3. The latest jar is copied to `releases/latest/`.
4. A versioned jar is copied to `releases/v<version>/`.
5. Release builds also mirror the newest compatible jar into `minecraft_versions/<minecraft-version>/`.

## Git and Releases

- `push_changes.bat` commits local changes and pushes `main`.
- `release_version.bat` shows the current version, defaults to the next version, asks for `all` or a supported Minecraft version, builds the jar, commits, tags the release as `v<version>-all` or `v<version>-mc<version>`, pushes it, and uploads the matching jar assets.
- Supported Minecraft versions are listed in `supported_minecraft_versions.txt`. The script will stop instead of pretending to build an older version that the current source tree cannot support.
