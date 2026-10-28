# Spawner Finder client (Forge 1.20.1)

Client-side mod with a control panel (Right Shift):
- Spawner finder: boxes, tracers, labels, HUD list, mob filter, range
- Villager finder: same options, filter by profession (+ wandering trader)
- Every option has an on/off switch, settings are saved in config/spawnerfinder.properties
- K = master on/off

For private/friendly servers only (with the admin's OK).

Build on GitHub: upload the contents of this folder (keep .github), Actions -> "Build mod" -> Run workflow -> download the artifact.
Local build: Gradle 8.1.1 + Java 17, `gradle build`, jar in build/libs/.
