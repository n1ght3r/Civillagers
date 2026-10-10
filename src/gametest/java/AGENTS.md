# src/gametest — automated in-game tests

Server-side tests that run in a real Minecraft server with real registries, headlessly, in seconds.

```bash
./gradlew ":26.3.x:runGametest"
```

```bash
./gradlew ":1.21.1-neoforge:runGametest"
```

`-Ptests=Boot,VillageData` runs only those classes (with or without the `GameTest` suffix, case
ignored). `-PgametestHeap=2g` raises the 1536 MB heap cap. Both are in `gradle/shared.gradle.kts`.

## What this is

GameTest is Mojang's framework (`net.minecraft.gametest.framework`). The runner replaces the dedicated
server: it skips the EULA, places each test in its own patch of a scratch world, exits non-zero if a
required test failed, and writes a JUnit report to `versions/<node>/build/gametest/report.xml`.

`civillagers_gametest` is its own small mod (`src/gametest/resources/fabric.mod.json`, and on NeoForge
`src/gametest/neoforge/resources/META-INF/neoforge.mods.toml`), so none of it reaches a published jar.

On NeoForge, `src/gametest/neoforge` holds `CivillagersGameTests`, a harness that reads the same
`fabric-gametest` list from `fabric.mod.json` and registers each test the way Fabric API does, so every
node runs one list of tests under one set of ids. `stonecutter.gradle.kts` swaps the test files'
`@GameTest` import on those nodes.

## Writing a test

- List the class under `fabric-gametest` in `src/gametest/resources/fabric.mod.json`. A class that is
  not listed is silently never run, on either loader.
- A test is a `public`, non-static method taking one `GameTestHelper`, annotated `@GameTest` with no
  arguments, ending in `helper.succeed()`.
- Assert with `TestFixtures.check(helper, condition, message)`: `helper.assertTrue` takes a `Component`
  from 1.21.5 and a string before it.
- Positions passed to `helper` are relative to the test's own patch of world.
