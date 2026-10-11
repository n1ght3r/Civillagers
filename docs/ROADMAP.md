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
| `runBenchmark` | What the mod costs on a fixed seed: village ticks with 1 / 10 / 50 / 100 villages, one village's memory, guards on patrol, in combat and with nobody near beside vanilla villagers and iron golems; each budget below marked met or missed. See `src/dev/java/com/civillagers/dev/benchmark/AGENTS.md` |
| JFR (`-Pprofile`) | Hot methods and allocation sites |
| Leak check (`tools/agent/smoke/leak.jsonl`) | A driven client opens and leaves a world with guards and a bell 5 times, then takes the JVM's class histogram: no `ServerLevel`, no server, no `Guard`, nothing of the mod's world-bound packages. Vanilla keeps the last `ClientLevel` until the next world opens, so the last round removes the guards first |
| Gametests | Every lifecycle rule has a test: create villages, stop the server, check every cache is empty |
| `/civillagers debug stats` | Dev command: village count, the scheduler's mean and max tick time, cache sizes |

**Target budgets** (server, as measured by the benchmark):

| Scenario | Budget |
|---|---|
| 10 villages, ~100 villagers, ~20 guards (a player near one village, the rest idle), 3 builders working | ≤ 0.5 ms/tick mean, ≤ 2 ms max |
| 20 guards on patrol / in combat | ≤ 1× / ≤ 2× what 20 vanilla iron golems cost in the same run |
| 20 guards with nobody near | ≤ 0.15 ms/tick mean |
| A village in unloaded chunks | ~0 ms (not ticked) |
| One village's data | ≤ ~4 KB in memory |
| After opening and closing a world 5 times | 0 `ServerLevel` and 0 `Guard` instances left |

---

## 2. Phases

Each phase is cut into steps small enough for one change each. A step is done when its own check passes
(a gametest, a benchmark figure, an agent script); a phase is done when every step is, and its budget is
met in `runBenchmark`. ✅ done, 🚧 in progress, ⬜ not started.

### Phase 0 — Foundation ✅
- Multi-version layout (Fabric 26.3, 26.2, 1.21.1, NeoForge 1.21.1), CI, gametests, automatic dependency bumps.

### Phase 1 — Village core and measurement ✅
*Nothing a player sees, but everything after builds on it.*
Status: everything below is in. On every node, 50 empty villages cost 0.007–0.026 ms/tick and 50 villages
in unloaded chunks 0.0003 ms/tick (`runBenchmark`, 2026-10-10). One typical village keeps about
3.4–3.8 KB, after member UUIDs were packed into `long` arrays and stockpile ids shared. The leak check
(`tools/agent/smoke/leak.jsonl`, 2026-10-10) leaves no server level, no guard and nothing of the mod's
world-bound classes on any node.
- `VillageData` (`SavedData` per dimension): id, center, radius, villager UUIDs, level, stockpile (counts, not items).
- Village detection from vanilla POIs (bell, beds, workstations); merging and splitting villages.
- `VillageIndex`: the chunk → village index.
- `VillageScheduler`: staggered, budgeted ticking, only for villages whose chunks are loaded.
- Lifecycle hooks in `platform/Loader`: `onServerStarted`, `onServerStopped`, `onServerTickEnd`.
- `runBenchmark` and `/civillagers debug stats`.
- **Done when:** gametests for creating, saving, reloading and merging villages pass; the benchmark with 50 empty villages is ≤ 0.1 ms/tick; the leak check passes.

### Phase 2 — Guards ✅
A `Guard` entity of its own (vanilla professions only change trades), on a `GoalSelector` (cheap, easy to
control) rather than a `Brain`.

**Look (requested):** a villager (villager head with the big nose, villager robe) with two separate arms
like a player's, showing the weapon or shield it holds and the armor it wears. Built on `HumanoidModel`
with an 8×10×8 head and the robe; `ItemInHandLayer` and `HumanoidArmorLayer`; the helmet stretched over
the taller head; the robe hidden under leggings; vanilla's villager texture read by path (never copied).
A guard skin of its own (64×64) can come later, made or licensed for the mod. The one exception to "never
copied": the Guard Spawn Egg's 16×16 texture is vanilla's villager egg with an iron helmet painted over
the head (option A of five drawn, chosen 2026-10-10); the same file on every version, untinted on 1.21.1.

| Step | What | Check | |
|---|---|---|---|
| 2.1 | Entity, attributes, spawn egg, `en_us`/`vi_vn` | `aNewGuardCarriesASword` | ✅ |
| 2.2 | Model and renderer on 1.21.1 and 26.x (`client/platform/GuardModel`, `GuardRenderer`; since 2.17 `CivVillagerModel`, `CivVillagerRenderer`) | `tools/agent/shots/guards.jsonl` on every client node | ✅ |
| 2.3 | Gear: hand over by using an item, take back sneaking | `aGuardWearsWhatItIsGiven`, `aPlayerHandsAGuardASword` | ✅ |
| 2.4 | Rate-limited target scan; melee and bow | `aGuardLooksForTargetsAtMostEveryTenTicks`, `aGuardGoesAfterAHostileMob` | ✅ |
| 2.5 | Patrol inside the village, per-tick path budget | `patrolPointsStayInTheVillage` | ✅ |
| 2.6 | Sleep in a free bed at night; head for a raid | `aGuardFindsAFreeBed` | ✅ |
| 2.7 | Guards counted per village (`Village.guards`) | `savesAndReloadsEveryField` | ✅ |
| 2.7b | Spawn egg texture of its own | `tools/agent/shots/guard-egg.jsonl` on every client node | ✅ |
| 2.8 | Decide the guard budget: against iron golems measured in the same run (see below) | `runBenchmark` budgets | ✅ |
| 2.9 | Lighter AI with no player within 48 blocks: no patrol, no idle look, a scan every 40 ticks, and at rest the AI and movement one tick in four | `aGuardIdlesWithNobodyNear`, benchmark "guards, nobody near" | ✅ |
| 2.10 | Combat path budget (`GuardCombatPath`): search again only when the target moved more than 2 blocks or the path ended, at most every 10 ticks, 8 searches per server tick | `aFightingGuardSearchesOnlyWhenItsTargetMoves`, benchmark "guards in combat" | ✅ |
| 2.11 | Raise the shield against a target that shoots, until it is in reach, and against a creeper about to blow | `aGuardRaisesItsShieldAgainstArchersAndCreepers`, `shots/guards.jsonl` | ✅ |
| 2.12 | Accept crossbows (charge, hold, shoot, the crossbow poses) | `aGuardShootsALoadedCrossbow`, `aGuardTakesACrossbowAndGivesItBackUnloaded`, `shots/guards.jsonl` | ✅ |
| 2.13 | Zombies and raiders (not zombified piglins, drowned or witches) target guards as they target villagers (`MobMixin`) | `zombiesAndRaidersHuntGuards` | ✅ |
| 2.14 | Leak check: open and leave a world with guards 5 times, then a class histogram: no `ServerLevel`, no `Guard`, nothing world-bound of the mod | `tools/agent/smoke/leak.jsonl` on every client node | ✅ |
| 2.15 | A villager's 20 health; named Villager Guard; the clothes of its biome's villager type (saved, synced, drawn as a layer over vanilla's `type/<biome>.png`) | `aNewGuardCarriesASword`, `aGuardWearsTheClothesOfItsBiome`, `shots/guards.jsonl` | ✅ |
| 2.16 | A guard a zombie kills rises as a vanilla zombie villager with its gear (villager odds); cured, it is a guard again (`ZombieGuards`, since 2.17 `ZombieVillagers`; `ZombieMixin`, `ZombieVillagerMixin`) | `aGuardTurnsWithAVillagersOdds`, `aGuardAZombieKillsOnHardRisesAsAZombie`, `aZombieGuardKeepsItsGearAndIsCuredBackToAGuard`, `anOrdinaryZombieVillagerIsLeftToVanilla` | ✅ |
| 2.17 | `CivVillager`, the base the chief and builders will extend (look, clothes, idling, saving, zombies, renderer); an addon API in `com.civillagers.api` (goal and threat hooks, zombie hooks, `civillagers:guard_weapons`) | `AddonApiGameTest`, every `GuardGameTest` | ✅ |
| 2.18 | Villager life: civilians (a disarmed guard) flee and hide, an armed guard rallies to a rung bell, all meet at the bell in vanilla's hours, celebrate a won raid and shake their heads; trades, breeding, gossip, golems, witches left out (`docs/knowledge/villager-life.md`) | `VillagerLifeGameTest` | ✅ |
| 2.19 | Guard levels 1 to 5 at vanilla's thresholds, with vanilla's badge; experience from trades (two per level from a pool) and from what it kills (the experience the mob would drop, gained at once); a level upgrades its own gear by table (never a stat), its own gear never drops or wears out; a spawn egg makes any level; level, experience and trades survive a zombie and the cure | `GuardLevelGameTest`, `aZombieGuardKeepsItsGearAndIsCuredBackToAGuard`, `aPlayerHandsAGuardASword` | ✅ |
| 2.20 | Trades checked against vanilla 26.3's trade data and brought in line; patrol and meeting points on dry ground only and water twice as costly to path through; in a raid each guard hunts the nearest raider (target within 24 blocks, walks towards one within 96) and the bell rally waits; meeting partners no longer pull each other's heads | `patrolAndMeetingPointsAreNeverOnWater`, `aGuardGoesForTheNearestRaider` | ✅ |

**Budget (2.8, decided 2026-10-10).** The first target was "20 guards ≤ 0.15 ms/tick", below what any
vanilla mob costs under this measurement: each guard's whole tick, its AI, its pathfinding and the
movement and collision vanilla does for any mob. The budget is set against vanilla's closest mob, the
iron golem, measured in the same run, so it does not depend on the machine:

- 20 guards on patrol cost no more than 20 iron golems (ratio ≤ 1.0);
- 20 guards in combat cost no more than twice as much (ratio ≤ 2.0);
- 20 guards with nobody near cost ≤ 0.15 ms/tick (2.9);
- target scans stay at most one per guard per 10 ticks.

Measured with `runBenchmark -Pbenchmark=standard` on every node (2026-10-10): patrol 0.74–0.89× the
golems (0.41–0.58 ms for 20, 21–29 µs a guard), combat 1.02–1.60× (0.59–0.92 ms), nobody near
0.10–0.13 ms (about 5.5 µs a guard), 0.77 scans per 10 ticks. Vanilla's own profiler (`/perf`) splits a
patrolling guard's tick the way it splits a golem's, AI about 38% and movement about 35%, so a guard on
patrol is a golem's cost by nature; the quick profile's shorter window scatters by ±15% and has once read
1.07 on NeoForge, so the standard profile's figures are the reference.

**Done when:** 2.1–2.10 and 2.14 are done and their checks pass on every node; 2.11–2.13 may move to a
later release. Done 2026-10-10, 2.11–2.13 included.

### Phase 3 — Village chief ⬜
One chief per village, a slow decision loop driven by needs, and a plan in the village data that guards
(and later builders) act on.

| Step | What | Check |
|---|---|---|
| 3.1 | Pick a chief from the members (oldest, or a villager with a bell nearby); store its UUID in `Village` | gametest: a village with members has a chief after one update |
| 3.2 | Succession: a dead or missing chief is replaced on the next slow update | gametest |
| 3.3 | Needs, computed from what the village already counts: housing (beds vs members), defense (guards vs members and raids seen), food | pure unit gametests on fixed counts |
| 3.4 | The plan: a short, capped list in `Village` ("1 more house", "2 more guards"), saved with the codec | save/reload gametest |
| 3.5 | Decisions on `VillageScheduler`'s staggered interval, never per tick | benchmark: ≤ 0.05 ms per village per run |
| 3.6 | Recruitment: "needs a guard" turns an unemployed villager into a guard, slowly and capped (open question 4) | gametest |
| 3.7 | `/civillagers debug here` shows the chief, needs and plan; `server.villages` answers them | agent script |
| 3.8 | Village info screen, synced only while open, rate-limited | agent script + manual |

**Done when:** 3.1–3.7 pass and 3.5's budget is met; 3.8 can follow.

### Phase 4 — Builders ⬜

| Step | What | Check |
|---|---|---|
| 4.1 | Structure library: `.nbt` in `data/civillagers/structure/` (a house first), loaded through `StructureTemplateManager`, datapack-extensible | gametest loads every template |
| 4.2 | Site selection from heightmaps on a coarse grid, spread over several ticks, only in loaded chunks | gametest: a site is flat enough and inside the village |
| 4.3 | Build cursor `(template, index)` in `Village`, saved | save/reload gametest resumes at the right block |
| 4.4 | Placement: at most K blocks per tick per builder and a global cap, skipping unloaded chunks | benchmark: 3 builders ≤ 0.2 ms/tick |
| 4.5 | A builder entity (or profession) that walks to the site and places what the cursor says | gametest + agent shot |
| 4.6 | The chief's plan starts builds; a finished build updates the counts | gametest |
| 4.7 | (Open question 2) Building costs stockpile resources | gametest |

**Done when:** 4.1–4.6 pass, 4.4's budget is met, and no chunk is ever force-loaded.

### Phase 5 — Village growth ⬜

| Step | What | Check |
|---|---|---|
| 5.1 | Village levels from population and buildings, saved | gametest |
| 5.2 | Eras (wood → stone → brick → walls) choose the structure set | gametest |
| 5.3 | Population grows with housing (breeding helped or allowed by free beds) | gametest |
| 5.4 | Walls and watchtowers; guards post on towers | agent shot |
| 5.5 | Benchmark scenario: 10 max-level villages | within every budget in 1.4 |

### Phase 6 — Polish and 1.0 ⬜

| Step | What |
|---|---|
| 6.1 | Config (file, then Mod Menu / NeoForge screen) with every performance limit configurable |
| 6.2 | Compatibility: MCA, Guard Villagers, Lithium, ModernFix |
| 6.3 | Documentation, Modrinth and CurseForge pages, release scripts |
| 6.4 | A guard skin of its own |

### After 1.0 (ideas)
- Relations between villages: trade, alliances, war.
- Chief personalities (aggressive / mercantile / builder).
- NeoForge 26.x once there is enough demand.

---

## 3. Open questions before phases 3–4

1. ~~Are guards their own entity or villagers with a new profession?~~ Their own entity (phase 2).
2. Do builders **cost resources**, or build for free over time?
3. Can players **give orders** to a village (through the chief), or are villages fully autonomous?
4. Do generated villages start with guards and a chief, or do they have to earn them?
