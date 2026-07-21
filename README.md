<p align="center">
  <img src="src/main/resources/assets/zenithclient/icon.png" width="96" alt="ZenithClient logo">
</p>

# ZenithClient

ZenithClient is a client-side Fabric utility mod for Minecraft 26.2 built with JDK 25.

## Features

### Visuals

- Player ESP: 3D box, 2D box, corner box, fill, outline, tracers, name tags, color, thickness, opacity, and range.
- Entity ESP: target groups, shape modes, fill/chams, outline, tracers, name tags, color, thickness, opacity, and range.
- Item ESP: dropped item boxes, labels, color, range, and tracers.
- Projectile ESP: projectile boxes, labels, color, range, and tracers.
- Block ESP: configurable block group, color, fill opacity, radius, and cached low-lag scanning.
- Trajectories: projectile path preview with quality, color, thickness, and start-distance controls.
- X-Ray: chunk-rebuild toggle that shows selected valuable blocks while hiding non-target block geometry.

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

## Git and Releases

- `push_changes.bat` commits local changes and pushes `main`.
- `release_version.bat` shows the current version, optionally updates `mod_version`, builds the jar, commits, tags `v<version>`, pushes the tag, and uploads the jar from `releases/latest/` to the GitHub release.
