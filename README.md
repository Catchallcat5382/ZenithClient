<p align="center">
  <img src="docs/zenith-banner.png" width="100%" alt="ZenithClient banner">
</p>

<p align="center">
  <strong>Precision. Performance. Control.</strong><br>
  A modular Fabric client built for combat, rendering, movement, and full in-game control.
</p>

---

# ZenithClient

ZenithClient is a configurable Minecraft client with a custom Click GUI and a growing set of combat, render, movement, and HUD modules. Every module can be toggled from the menu, assigned its own keybind, and configured from its settings page.

## Modules

### Render

- Player ESP, Entity ESP, Item ESP, and Projectile ESP
- Model outlines, boxes, fills, tracers, labels, colors, thickness, and range
- Block ESP with registry-based block selection
- X-Ray with selectable block lists and controlled renderer rebuilding
- Projectile trajectory prediction
- Fullbright, No Blindness, and No Fire Overlay
- Freecam with independent camera movement

### Combat

- Criticals
- Attribute Swap with configurable hotbar slot and restore delay
- Auto Totem
- Kill Aura with a selectable entity target list
- Reach and Infinite Reach controls
- Mace Kill with a small configurable simulated fall

### Movement

- Flight with separate horizontal and vertical speeds
- Speed
- Auto Sprint
- No Slow
- No Stun
- No Fall
- Air Jump

### HUD

- FPS display
- Coordinates display
- Toggle notifications

## Target lists

Entity ESP, Kill Aura, Block ESP, and X-Ray use searchable multi-select registry lists. Open the module settings and click or right-click the target row to choose multiple entities or blocks. Selected entries are saved as one module list instead of replacing the previous choice every time.

## Controls

- **Right Shift** — open or close ZenithClient
- **Left-click a module** — toggle it
- **Right-click a module** — open its settings
- **Click or right-click a target-list row** — open the multi-select registry menu
- **Escape** — return to the previous screen

## Building

1. Install **JDK 25**.
2. Run `build.bat`.
3. Choose one Minecraft version, a comma-separated list, or `all`.
4. Choose the next ZenithClient version.
5. The version is saved only after the requested build completes successfully. Failed builds restore the previous version automatically.

Successful JAR files are placed in `minecraft_versions/`, `releases/v<version>/`, and `releases/latest/`.

## Latest update

- Fixed the Minecraft 26.2 compilation failure caused by the missing `VisualExtrasState` import in the Click GUI.
- Restored access to Zoom, Clear Weather, Daylight, No Hurt Camera, and No Portal Overlay from the Visuals tab.
- Entity selection still merges built-in, modded, saved, and current-world entity types.
- Freecam still uses progressive loaded-chunk visibility rebuilding and detached-camera occlusion handling.
- Failed builds continue restoring the previous project version automatically.

---

<p align="center">
  <sub>ZenithClient is an independent project and is not affiliated with Mojang or Microsoft.</sub>
</p>
