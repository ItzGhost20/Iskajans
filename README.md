# Spawner Finder (Forge 1.20.1)

Client-side mod: draws colored boxes around mob spawners through walls
and lists the 8 nearest ones (type, distance, coords) on screen.
Key K toggles it. For private/friendly servers only (with the admin's OK).

Build on GitHub (no PC needed):
1. Create a GitHub repo and upload the contents of this folder (keep the .github folder).
2. Open the Actions tab -> "Build mod" -> run it.
3. Download the artifact "spawnerfinder-jar" and put the jar in .minecraft/mods.

Local build: Gradle 8.1.1 + Java 17, run `gradle build`; the jar is in build/libs/.
