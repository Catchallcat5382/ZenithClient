# ZenithClient

ZenithClient is a client-side Fabric utility mod for Minecraft 26.2. The project is compiled with JDK 25.

## Menu

Press **Right Shift** to open ZenithClient. The keybind can be changed in Minecraft's Controls menu.

### Visuals tab
- Player ESP with selectable 3D box, 2D box, corner box, tracers, name tags, color, fill, thickness, and range settings.
- Entity ESP with selectable targets: players, hostile mobs, passive mobs, zombies, creepers, skeletons, all mobs, or all entities.
- Entity ESP controls for shape, outline, fill/chams, tracers, name tags, color, opacity, thickness, and range.
- Block ESP toggle.
- Selectable block targets: valuable ores, all ores, containers, spawners, ancient debris, or diamond ore.
- Trajectory preview with simulation quality, line color, thickness, and start distance.
- X-Ray with selectable visible block mode and hidden-block opacity.

### Misc tab
- FPS HUD.
- Coordinates HUD.
- Fullbright.
- Auto Sprint.

## Build with Java 25
1. Install a full JDK 25 distribution.
2. Open Command Prompt and run `java -version`. The first line must report version 25.
3. Double-click `build.bat`.
4. The builder downloads Gradle 9.4.0 into `.gradle-bootstrap` on its first run.
5. The newest mod JAR is copied to `releases/latest/`.
6. A clean versioned copy is stored under `releases/v<version>/`.

The Java checker safely captures the normal stderr output produced by `java -version`, so a valid Java 25 installation is no longer mistaken for an error.

## Git and releases

Run `push_changes.bat` to initialize git if needed, commit changes, and push to `origin`.

Run `release_version.bat` to show the current version, optionally update `mod_version`, build the JAR, commit, tag `v<version>`, push the tag, and create a GitHub release when GitHub CLI is available.

## Attribution

Meteor Client render behavior was used as an open-source reference. See `METEOR_ATTRIBUTION.md`.
