# src/dev — development-only tooling

Tools for working on the mod that must never ship. Like `src/gametest`, this is its own source set and its
own small mod, so none of it can reach a published jar: `civillagers-dev` on Fabric
(`src/dev/resources/fabric.mod.json`) and `civillagers_dev` on NeoForge
(`src/dev/neoforge/resources/META-INF/neoforge.mods.toml`), since a NeoForge mod id cannot contain a
hyphen. `runServer`, `runClient` and `runBenchmark` load it; `runGametest` and `runDatagen` do not.

Three tools:

- `/civillagers benchmark`: what the mod costs a server. How to run it and what it measures is in
  [benchmark/AGENTS.md](com/civillagers/dev/benchmark/AGENTS.md).
- `/civillagers showcase`: a row of motionless guards in different gear in front of the player, to look at
  the model, held items, armor and poses (a drawn bow, a raised shield, a loaded crossbow). `./gradlew ":<node>:runClient" -Pshowcase` runs it by itself a few
  seconds after a world opens.
- The agent client: a file queue an agent drives a real client or server through, and a driven mode in
  which a client never takes the mouse pointer. See [agent/AGENTS.md](com/civillagers/dev/agent/AGENTS.md)
  and `tools/agent/`.

| Where | What |
|---|---|
| `com.civillagers.dev.CivillagersDev` | The entrypoint both loaders call: registers the command, the tick, the autorun |
| `com.civillagers.dev.benchmark` | The benchmark itself, loader independent |
| `com.civillagers.dev.agent` | The agent: `core/` (plain Java), `game/` (any process), `client/` (clients only) |
| `com.civillagers.dev.mixin` | `ServerLevelMixin`, which times each entity's tick for the benchmark; `GuardMixin`, which lets the benchmark tell guards whether a player is near; `MouseGrabMixin`, which keeps a driven client off the mouse |
| `com.civillagers.dev.platform.DevVanilla` | Where the dev tools alone meet a Minecraft version difference |
| `src/dev/<loader>/.../platform/DevLoader` | The loader calls the mod's own `Loader` does not have, one copy per loader |
| `src/dev/<loader>/.../<loader>/` | Each loader's entrypoint |

## Rules

- Nothing in `main`, `client` or `gametest` may reference this source set.
- `CivillagersDev` registers nothing unless `Loader.isDevelopmentEnvironment()` is true (every run task),
  or `-Dcivillagers.dev=true`.
- Everything here compiles on every node; CI builds `devClasses` as part of `ci` and runs neither tool.
- A Minecraft version difference goes behind `DevVanilla`, a loader difference behind `DevLoader`, the
  same way `platform/` works for the mod. `checkVersionSeam` does not look here; keep to it anyway.
- No change to the mod for a tool's sake beyond what the mod wants anyway: the benchmark reads
  `GuardStats`, which `/civillagers debug stats` shows too, times vanilla entities from its own mixin, and
  answers the guard's own "is a player near" question (`Guard.anyPlayerWithin`) from another.
- Mixins follow the mod's rules: package-private, `abstract`, every injected member prefixed `civ$`,
  listed in `civillagers.dev.mixins.json`.
