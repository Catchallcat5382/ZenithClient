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
2. Run `build.bat`, then type `all` for every supported version or a specific version such as `1.20.1`.
3. The newest jars are copied to `releases/latest/`.
4. Versioned jars are copied to `releases/v<version>/<minecraft-version>/`.
5. The current jar for each Minecraft version is mirrored into `minecraft_versions/<minecraft-version>/`.

`build.bat` is the recommended build selector. Type `all` to build 26.2, 26.1.2, 26.1.1, 26.1, 1.21.11, 1.21.10, 1.21.9, 1.21.8, 1.21.7, 1.21.6, 1.21.5, 1.21.4, 1.21.1, 1.20.6, 1.20.5, 1.20.4, and 1.20.1. You can also type one version, or a comma-list like `26.2,1.20.1`.

## Git and Releases

- `push_changes.bat` commits local changes and pushes `main`.
- `release_version.bat` shows the current version, defaults to the next version, asks for `all` or a supported Minecraft version, builds the jar, commits, tags the release as `v<version>-all` or `v<version>-mc<version>`, pushes it, and uploads the matching jar assets.
- Supported Minecraft versions are listed in `minecraft_build_versions.csv`. The script will stop instead of pretending to build an older version that the current source tree cannot support.
