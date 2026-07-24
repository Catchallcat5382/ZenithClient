<p align="center">
  <img src="docs/zenith-banner.png" width="100%" alt="ZenithClient banner">
</p>

<p align="center">
  <strong>Precision. Performance. Control.</strong><br>
  A dark orange client-side Fabric utility mod for Minecraft, built with JDK 25.
</p>

---

## ZenithClient

ZenithClient combines a custom orange-and-charcoal Click GUI with configurable visual, movement, combat, and HUD utilities. The menu uses the same branding as the banner above, including a compact top-left emblem that expands into the full ZenithClient banner when hovered.

## Highlights

| Area | Included features |
| --- | --- |
| **Visuals** | Player ESP, Entity ESP, Item ESP, Projectile ESP, Block ESP, trajectories, X-Ray, No Blindness, and No Fire Overlay |
| **Combat** | Criticals, Auto Totem, Attribute Swap, Kill Aura, Reach, Infinite Reach, and bounded Mace Kill controls |
| **Movement** | Flight, Speed, Auto Sprint, No Slow, No Stun, No Fall, Air Jump, and independent Freecam |
| **HUD** | Fullbright, FPS display, and coordinates |

## Controls

- Press **Right Shift** in a world to open ZenithClient.
- **Left-click** a module card to toggle it.
- **Right-click** a module card to open its settings.
- Numeric settings support sliders and direct typing.
- The top-left Zenith emblem expands into the full banner while hovered.

## Build

1. Install **JDK 25**.
2. Run `build.bat`.
3. Choose `all` or a supported Minecraft version.
4. Built JAR files are copied into the existing version and release folders used by the project.

## Project layout

- Client source: `src/client/java/com/zenithclient/client/`
- Resources: `src/main/resources/assets/zenithclient/`
- Current per-version JARs: `minecraft_versions/`
- Release outputs: `releases/`

## Git and releases

- `push_changes.bat` commits and pushes the current project to `main`.
- `release_version.bat` builds, commits, tags, pushes, and uploads the selected release artifacts.
- The README banner is stored at `docs/zenith-banner.png`, so it appears automatically on GitHub after these files are committed.

---

<p align="center">
  <sub>ZenithClient is an independent client-side Fabric project and is not affiliated with Mojang or Microsoft.</sub>
</p>
