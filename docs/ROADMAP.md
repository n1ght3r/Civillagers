# Civillagers — Roadmap

Goal: villages that grow into civilizations with **guards**, a **village chief** and **builders**,
**without weighing down the server**. Performance and memory are requirements, not later polish: a phase
is only done when it meets its performance budget.

---

## 1. Performance and memory rules (every phase)

### 1.1 No memory leaks: every piece of state has an owner and a lifecycle

Leaks in Minecraft mods almost always have one cause: **a long-lived object (a static, a singleton, a
cache) holding a reference to something that belongs to a world** (`Entity`, `Level`, `ServerPlayer`,
`LevelChunk`, `MinecraftServer`). In singleplayer every world you leave and reopen is a new server, so
whatever is retained piles up with each one.

| Rule | How |
|---|---|
| **No world data in statics** | Village data lives in each `ServerLevel`'s `SavedData`, so it is freed with the level. No `static Map<..., Village>`. |
| **Store UUIDs, not entities** | A village stores the `UUID`s of its villagers and guards and looks them up with `ServerLevel.getEntity(uuid)` when needed; the result is kept for one tick at most. |
| **Every cache has a clear** | Every temporary cache is cleared in `Loader.onServerStopped` (client side: on disconnect). A new cache gets its clear in the same change. |
| **No unbounded lists** | History, logs, "threats seen" and the like are ring buffers or have a hard cap. An unbounded list is a leak in disguise. |
| **Listeners register once** | Event listeners are registered when the mod initializes, never per entity or per village. |
| **No `WeakReference` as a patch** | The fix is clear ownership, not hoping the GC guesses right. |
| **No chunk force-loading** | No chunk tickets. Builders only work in chunks that are already loaded and resume when they load again. |

### 1.2 Cheap ticks: do less, less often, in slices

| Rule | How |
|---|---|
| **Slow decisions** | The chief "thinks" every ~100 ticks (5 seconds), not every tick. |
| **Staggered** | Village *i* runs on ticks where `(gameTime + hash(id)) % interval == 0`, so villages do not all land on the same tick. |
| **Per-tick budget** | `VillageScheduler` caps the villages processed per tick and the time spent (measured with `System.nanoTime`, 0.5 ms by default). Whatever is left over carries to the next tick. Configurable. |
| **Rate-limited world scans** | Guards look for targets every 10–20 ticks, in a bounded AABB (`getEntitiesOfClass`), never every tick. |
| **Frugal pathfinding** | Recompute a path only when the target has moved more than N blocks; cap path computations per tick. |
| **Spatial lookup** | A `Long2IntOpenHashMap` index (chunk → village id), never a loop over every village to ask "which village is this position in". |
| **Heightmaps** | Build sites are chosen from `Heightmap`s on a coarse grid, never by scanning blocks. |
| **Main thread only** | Minecraft's world is not thread-safe. Other threads only do pure computation on snapshots and never read or write the world. |

### 1.3 Few allocations in hot paths

- In per-tick loops: reuse `BlockPos.MutableBlockPos`; no streams or capturing lambdas; no boxing (use
  fastutil collections, which ship with Minecraft).
- Structure templates (`.nbt`) are loaded **once** through `StructureTemplateManager`, which vanilla
  already caches. Build progress is a compact cursor `(templateId, index)`, never a copied block list.
- Client sync: only on change, only to players who need it (for example someone with the village GUI
  open), rate-limited. Never a packet per tick.
- `SavedData.setDirty()` only when data actually changed. Saved data uses compact `Codec`s.

### 1.4 Measure, don't guess

| Tool | For |
|---|---|
| `runBenchmark` (ported from ThirstWasTaken2) | The mspt (milliseconds per tick) the mod costs with 1 / 10 / 50 villages, on a fixed seed |
| JFR (`-Pprofile`) | Hot methods and allocation sites |
| Leak check | Open and close a singleplayer world 5 times, then `jcmd <pid> GC.class_histogram`: the `ServerLevel` and mod class instance counts must be 0 |
| Gametests | Every lifecycle rule has a test: create villages, stop the server, check every cache is empty |
| `/civillagers debug stats` | Dev command: village count, the scheduler's mean and max tick time, cache sizes |

**Target budgets** (server, as measured by the benchmark):

| Scenario | Budget |
|---|---|
| 10 villages, ~100 villagers, ~20 guards, 3 builders working | ≤ 0.5 ms/tick mean, ≤ 2 ms max |
| A village in unloaded chunks | ~0 ms (not ticked) |
| One village's data | ≤ ~4 KB in memory |
| After opening and closing a world 5 times | 0 `ServerLevel` instances left |

---

## 2. Phases

### Phase 0 — Foundation ✅
- Multi-version layout (Fabric 26.3, 26.2, 1.21.1, NeoForge 1.21.1), CI, gametests, automatic dependency bumps.

### Phase 1 — Village core and measurement
*Nothing a player sees, but everything after builds on it.*
- `VillageData` (`SavedData` per dimension): id, center, radius, villager UUIDs, level, stockpile (counts, not items).
- Village detection from vanilla POIs (bell, beds, workstations); merging and splitting villages.
- `VillageIndex`: the chunk → village index.
- `VillageScheduler`: staggered, budgeted ticking, only for villages whose chunks are loaded.
- Lifecycle hooks in `platform/Loader`: `onServerStarted`, `onServerStopped`, `onServerTickEnd`.
- Port `runBenchmark` and `/civillagers debug stats` from ThirstWasTaken2.
- **Done when:** gametests for creating, saving, reloading and merging villages pass; the benchmark with 50 empty villages is ≤ 0.1 ms/tick; the leak check passes.

### Phase 2 — Guards
- A `Guard` entity of its own (not a villager profession: vanilla professions only change trades).
- `GoalSelector` (cheap, easy to control) rather than a full `Brain`.
- Patrol the village, attack hostile mobs, defend during raids, return to bed at night.
- Weapons and armor; players can hand them gear.
- Model and renderer (Blockbench), `en_us` and `vi_vn` lang.
- **Done when:** 20 guards cost ≤ 0.15 ms/tick; each guard scans for targets at most once every 10 ticks; combat and patrol gametests pass.

### Phase 3 — Village chief
- One chief per village, chosen from its villagers, with succession when the chief dies.
- A slow decision loop (~5 seconds, staggered) driven by **needs**: housing, defense, food, expansion.
- The output is a **plan** written to `VillageData`, such as "needs 1 more house" or "needs 2 more guards".
- A village info GUI; synced only while it is open.
- **Done when:** a chief's decision costs ≤ 0.05 ms per village per run; succession and needs gametests pass.

### Phase 4 — Builders
- A library of `.nbt` structures in `data/civillagers/structure/` (houses, walls, watchtowers, farms), extensible by datapack.
- Site selection from heightmaps on a coarse grid, spread over several ticks.
- Incremental placement: at most *K* blocks per tick per builder plus a global cap, only in loaded chunks, with a progress cursor so building resumes after a reload.
- (Optional) Building costs resources from the village stockpile.
- **Done when:** 3 builders at once cost ≤ 0.2 ms/tick; quitting mid-build and coming back resumes at the right block; no chunk is ever force-loaded.

### Phase 5 — Village growth
- Village levels and eras: wood → stone → brick → walls.
- The chief unlocks new structures by level; population grows with housing.
- **Done when:** the benchmark with 10 max-level villages stays within the budgets in 1.4.

### Phase 6 — Polish and 1.0
- Config screen (Mod Menu / NeoForge), with every performance limit configurable.
- Documentation, Modrinth and CurseForge pages, release scripts (ported from ThirstWasTaken2).
- Compatibility checks with popular villager mods (MCA, Guard Villagers) and performance mods (Lithium, ModernFix).

### After 1.0 (ideas)
- Relations between villages: trade, alliances, war.
- Chief personalities (aggressive / mercantile / builder).
- NeoForge 26.x once there is enough demand.

---

## 3. Open questions before phases 2–4

1. Are guards **their own entity** (proposed) or **villagers with a new profession**?
2. Do builders **cost resources**, or build for free over time?
3. Can players **give orders** to a village (through the chief), or are villages fully autonomous?
4. Do generated villages start with guards and a chief, or do they have to earn them?
