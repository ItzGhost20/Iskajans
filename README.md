# Spawner Finder client (Forge 1.20.1)

Client-side mod. Control panel: Right Shift. K = master on/off. G = AutoDig start/stop.

Tabs: Spawner | Villager | Colors | AutoDig | General

- Spawner finder / Villager finder: boxes, tracers, labels, HUD list, filter, range
- Colors: pick the color of every spawner type / villager profession (RGB sliders + presets)
- General: opacity, line width, label size, sound alert, copy nearest coords
- AutoDig (OFF by default): goes to the nearest spawner, mines the path with a normal pickaxe and the
  spawner itself with a silk touch pickaxe. Needs both pickaxes in the hotbar.
  Stops on damage / low health / manual movement, never mines chests or other block-entity blocks,
  and does not try to get around protected blocks.

Settings are saved in config/spawnerfinder.properties.
For private/friendly servers only (with the admin's OK).

Build: upload the contents of this folder to GitHub (keep .github) -> Actions -> "Build mod" -> download the jar.
Local build: Gradle 8.1.1 + Java 17, `gradle build`, jar in build/libs/.
