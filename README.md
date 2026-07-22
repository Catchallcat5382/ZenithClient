<p align="center">
  <img src="src/main/resources/assets/zenithclient/icon.png" width="96" alt="ZenithClient logo">
</p>

# ZenithClient

ZenithClient is a client-side Fabric utility mod for Minecraft 26.2 through 1.20.1 built with JDK 25.

## Features

### Visuals

- Player ESP: exact model-hugging glow outline, tracers, name tags, color, and range.
- Entity ESP: exact model-hugging glow outline with target groups, tracers, name tags, color, and range.
- Item ESP: exact dropped-item glow outline, labels, color, range, and tracers.
- Projectile ESP: exact projectile glow outline, labels, color, range, and tracers.
- Block ESP: configurable block group, color, fill opacity, radius, and cached low-lag scanning.
- Trajectories: projectile path preview with quality, color, thickness, and start-distance controls.
- X-Ray: experimental safe chunk-rebuild toggle that shows selected ores/liquids while hiding non-target block geometry.
- No Blindness: removes blindness and darkness effects client-side.
- No Fire Overlay: hides the first-person burning overlay.
- Freecam: independent spectator-style camera movement with configurable speed and reduced fluid-fog glitches.

### Combat

- Criticals.
- Auto Totem: refills the offhand with a totem from inventory when one is available.
- Attribute Swap: temporarily swaps to a selected hotbar slot while attacking, then swaps back.
- Kill Aura, Reach, Infinite Reach, and Mace Kill controls with right-click settings pages.

### Movement

- Flight with horizontal speed, vertical speed, and sprint multiplier.
- Speed with configurable movement amount.
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

Press Right Shift during gameplay to open ZenithClient. The menu key is ignored on the title screen, and module keybinds only fire while in-world with no screen open, so typing in chat or another text screen will not toggle modules.

Right-click a module card to open its settings. Numeric settings support both dragging the slider and typing an exact value.
Entity ESP and Kill Aura settings include a `Choose entity` row. Click it, or right-click it, to open the searchable in-game entity picker.

Chat commands start with `.`. `.autovaultclip down`, `.autovaultclip up`, and `.autovaultclip highest` move to the next matching block level in that direction.
Press Tab while typing `.aut...` in chat to autocomplete `.autovaultclip`.

## Build

1. Install JDK 25.
2. Run `build.bat`, choose the mod version to build, then type `all` for every supported Minecraft version or a specific version such as `1.20.1`.
3. The newest highest-Minecraft-version jar is copied to `releases/latest/`.
4. Versioned jars are copied to `releases/v<version>/<minecraft-version>/`.
5. The current jar for each Minecraft version is mirrored into `minecraft_versions/<minecraft-version>/`.

`build.bat` is the recommended build selector. It shows the current mod version, suggests the next version, and lets you press Enter to keep the current version or type a new one. Type `all` to build 26.2, 26.1.2, 26.1.1, 26.1, 1.21.11, 1.21.10, 1.21.9, 1.21.8, 1.21.7, 1.21.6, 1.21.5, 1.21.4, 1.21.1, 1.20.6, 1.20.5, 1.20.4, and 1.20.1. You can also type one version, or a comma-list like `26.2,1.20.1`.

Each successful target embeds its own Minecraft and Fabric Loader dependency in `fabric.mod.json`, so a `1.21.11` jar declares `~1.21.11` instead of the latest Minecraft version. Successful builds also create root shortcuts like `build_26.2.bat` and `build_1.21.11.bat` for rebuilding one specific target.

## Git and Releases

- `push_changes.bat` commits local changes and pushes `main`.
- `release_version.bat` shows the current version, defaults to the next version, asks for `all` or a supported Minecraft version, builds the jar, commits, tags the release as `v<version>-all` or `v<version>-mc<version>`, pushes it, and uploads the matching jar assets from `minecraft_versions/`.
- Supported Minecraft versions are listed in `minecraft_build_versions.csv`. The script will stop instead of pretending to build an older version that the current source tree cannot support.
