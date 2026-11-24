# Quasar MC

Home of the Quasar MC sandbox: original mods for Minecraft 26.3 (NeoForge) and, later, the pack config.

Forks of other people's mods (Figura, LittleTiles, CreativeCore, ...) live in their own repos.

## Mods in this repo

| Folder | Mod | ID |
|---|---|---|
| `core/` | Quasar MC Core: shared code | `quasarmc_core` |
| `lighting/` | Quasar MC Lighting: colored block light + sky tint | `quasarmc_lighting` |
| `unbound/` | Quasar Unbound: new content and vanilla changes (golden weapons so far) | `quasarmc_unbound` |

## Versions

All shared versions live in the root `gradle.properties`:
Minecraft 26.3, NeoForge 26.3.0.115, Java 25.

Each mod's id, name and version live in its own `<folder>/gradle.properties`.

## Building

```
./gradlew build
```
Jars end up in `core/build/libs/` and `lighting/build/libs/`.

## Running the game from IntelliJ

Open the repo folder in IntelliJ and let Gradle sync. Then use the Gradle tool window:
`lighting > Tasks > mod development > runClient` starts the game with Core + Lighting loaded.

All runs share one `run/` folder at the repo root (ignored by git).
