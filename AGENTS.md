# Civillagers

A villager civilization mod for **Minecraft 26.3, 26.2 and 1.21.1** on **Fabric**, and **1.21.1** on
**NeoForge**: guards, a village chief, and builders who expand the village on their own.

- Mod id and resource namespace: `civillagers`. Java package: `com.civillagers`.
- The build layout follows ThirstWasTaken2 (`../ThirstWasTaken2`), trimmed to what this mod needs.
- Every area below has its own `AGENTS.md`; read it before changing that area.

## Build and run

One source tree, one jar per node. Nodes are the Gradle subprojects in `settings.gradle.kts`: `26.3.x`,
`26.2.x` and `1.21.1` on Fabric (`build.gradle.kts`), and `1.21.1-neoforge` (`build.neoforge.gradle.kts`).
The Fabric `1.21.1` jar also covers 1.21; the NeoForge one does not.

| Command | What it does |
|---|---|
| `./gradlew buildAndCollect` | Build every node, jars in `build/libs/` |
| `./gradlew ":<node>:build"` | Build one node |
| `./gradlew ":<node>:runServer"` | Fastest smoke test. A clean run logs `Civillagers initialized for Minecraft <version>` |
| `./gradlew ":<node>:runClient"` | Dev client |
| `./gradlew ":<node>:runGametest"` | Automated in-game tests, headless. `-Ptests=<Class,...>` runs only those. See [src/gametest/java/AGENTS.md](src/gametest/java/AGENTS.md) |
| `./gradlew ":<node>:runDatagen"` | Regenerate JSON into `src/main/generated/<mc version>/` (Fabric nodes only) |
| `./gradlew ":<node>:ci"` | **Exactly what CI runs on that node**: the jar, every check, the gametests |
| `./gradlew -p build-logic test` | build-logic's own tests |

- **Never hand-edit `src/main/generated/`.** Change the generator in `src/datagen`. The NeoForge node
  reads the Fabric output of the same Minecraft version; `checkNeoForgeResources` fails if a Fabric-only
  key survives into it.
- Each node runs in its own `run/<node>/` (worlds are not portable across versions). A new one needs its
  own `eula.txt`.
- Unqualified `./gradlew build` acts on the **active** node (`26.3.x`). `./gradlew "Set active project
  to <node>"` rewrites the versioned comments in `src/` for the IDE. **Run `./gradlew "Reset active
  project"` before committing.**

## Stack and constraints

- **Java**: 26.1+ runs on Java 25, 1.21.x on Java 21. The rule lives once, in build-logic's
  `requiredJava`. Code the `1.21.1` nodes compile uses nothing newer than Java 21.
- **Multi-version via [Stonecutter](https://stonecutter.kikugie.dev)**. Per-node values (dependency
  versions, compat ranges) live only in `stonecutter.properties.toml`. `stonecutter.gradle.kts` is the
  controller and holds the rename rules; `build.gradle.kts` is the Fabric script (Loom),
  `build.neoforge.gradle.kts` the NeoForge one (ModDevGradle), `gradle/shared.gradle.kts` what both
  share. `build-logic` is plain Kotlin the scripts call; it never depends on Loom, ModDevGradle or
  Stonecutter.
- **Split source sets.** Anything touching `net.minecraft.client` goes in `src/client/java`, never
  `src/main/java`. NeoForge compiles both together, so only the Fabric nodes catch a mistake.
- **Common code never names a mod loader.** `src/main/java` and `src/client/java` call
  `platform/Loader` and `client/platform/ClientLoader`, one copy per loader in `src/*/<loader>`.
  `checkLoaderSeam` enforces it. See [platform/AGENTS.md](src/main/java/com/civillagers/platform/AGENTS.md).
- **Version differences have one home.** A pure rename is a rule in `stonecutter.gradle.kts`. A real
  difference goes behind `platform/Vanilla` or `client/platform/ClientVanilla`, the only places (with
  `mixin/`) a `//?` block is allowed. `checkVersionSeam` enforces it.
- **Registration** goes through `Loader.onRegister(Registries.X, ...)`, never straight into a registry:
  NeoForge freezes the registries until its registration event.
- **Optional integrations are soft.** No hard dependency on another mod, ever. Gate on
  `Loader.isModLoaded` and keep the integration's classes off the load path when it is absent.
- **Mixins**: in `com.civillagers.mixin` (client ones in `com.civillagers.client.mixin`),
  package-private, `abstract`, every injected member prefixed `civ$`, listed in
  `civillagers.mixins.json` or `civillagers.client.mixins.json` or they silently do nothing.
- **Lang**: every key in `en_us.json` is in every other lang file (`vi_vn.json` today); `checkLang`
  fails otherwise.

## Where things go

| Package | What |
|---|---|
| `com.civillagers` | `Civillagers`, the mod's entry point, called by each loader's entrypoint |
| `com.civillagers.village` | The village as saved data: members, chief, stockpile, level, plans |
| `com.civillagers.entity` | Guard, village chief and builder entity types and professions |
| `com.civillagers.ai` | Brain behaviours: patrol, defend, gather, build, the chief's decisions |
| `com.civillagers.building` | Structure templates, build plans and step-by-step placement |
| `com.civillagers.platform` | `Loader` (per loader) and `Vanilla` (per version): the seams |
| `com.civillagers.client` | Client entry point, `render/` for renderers, `platform/` for client seams |

## Where to look

- [src/main/java/com/civillagers/platform/AGENTS.md](src/main/java/com/civillagers/platform/AGENTS.md): the loader and version seams.
- [src/gametest/java/AGENTS.md](src/gametest/java/AGENTS.md): writing and running gametests.
- [src/datagen/java/AGENTS.md](src/datagen/java/AGENTS.md): generated resources.

## Commits

Conventional prefixes: `feat:`, `fix:`, `build:`, `docs:`, `test:`, `chore:`. Before pushing, run
`./gradlew ":<node>:ci"` on every node the change touches.
