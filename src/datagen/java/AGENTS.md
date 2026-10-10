# src/datagen — generated resources

Fabric's data generation API writes the JSON the mod ships that is not worth writing by hand: recipes,
loot tables, tags, models, advancements. It runs on the Fabric nodes only:

```bash
./gradlew ":26.3.x:runDatagen"
```

- Output goes to `src/main/generated/<minecraft version>/`, keyed by Minecraft version, not by node, so
  `1.21.1` and `1.21.1-neoforge` share one directory. The output is committed.
- `runDatagen` empties that directory first, so what is on disk afterwards is exactly what the
  generators produce. `checkDatagen` (part of `ci`) regenerates and fails if git sees a difference.
- `datagen` is its own source set and its own small mod (`civillagers-datagen`), so no generator code
  reaches the published jar.
- Providers are added in `CivillagersDatagen`. The Fabric API renamed `FabricDataOutput` to
  `FabricPackOutput` and `FabricTagProvider` to `FabricTagsProvider` in 26.1; write the newer names, and
  `stonecutter.gradle.kts` gives the 1.21.1 node the older ones.
- Hand-written resources (lang files, textures, structure `.nbt` files) stay in `src/main/resources`.
