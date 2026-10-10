# /civillagers benchmark

What the mod costs a server, in time and memory, with nobody joining, measured against the budgets in
[docs/ROADMAP.md](../../../../../../../docs/ROADMAP.md) section 1.4. Every node runs it, the NeoForge one
included. [src/dev/java/AGENTS.md](../../../../AGENTS.md) is the source set it belongs to.

## Running it unattended

```bash
./gradlew ":26.3.x:runBenchmark"
```

Any node's name works there. It starts the dedicated server in `run/<node>/`, runs
`/civillagers benchmark` from the console as soon as the server is up, writes the report and stops the
server. Progress is printed as `[CivBenchmark] ...` lines, and the last one is

    [CivBenchmark] DONE status=ok in 61.0 s report=<absolute path>

The Gradle task exits 0 even when the benchmark failed, so read `status` in the report: `ok`, `cancelled`
or `failed`, with `message` saying why. The newest report is also copied to
`run/<node>/benchmark/latest.json`, and the console summary ends with each budget marked `met` or `MISS`.

The server refuses to start until `run/<node>/eula.txt` says `eula=true`; accepting the EULA is the
developer's to do, once per node directory.

`runBenchmark` shares `runServer`'s directory but not its world: it opens `civillagers-benchmark` beside
it (`--world`), generated from the fixed `civillagers.benchmark.seed` in `gradle.properties` that the
`benchmarkRunDirectory` task writes into `server.properties` when it has no seed. So a run never depends
on the dev world, and the Fabric and NeoForge nodes of one Minecraft version measure the same terrain.
Worldgen differs between Minecraft versions, so the same seed still gives different terrain across them.

`-Pbenchmark=<profile>` picks the scale:

| Profile | Villages | Simulated ticks | Guards | Server ticks |
|---|---|---|---|---|
| `quick` (default) | 1, 10, 50 | 200 + 1 000 | 1, 20 | 100 + 300 |
| `standard` | 1, 10, 50, 100 | 400 + 2 000 | 1, 10, 20, 50 | 100 + 400 |

`quick` takes about two and a half minutes on a desktop, most of it the guard scenarios, which run in
real time; `standard` about five minutes. A guard scenario warms up for 100 ticks and measures at least
300: every patrolling guard starts its first walk within eight seconds of spawning, and a shorter window
measures that burst rather than a patrol.

## Running it by hand

Start `./gradlew ":26.3.x:runServer"` and type `civillagers benchmark [quick|standard|status|cancel]` into
its console. In game it needs permission level 4. A run does at most 35 ms of work per server tick and
carries on over the following ticks.

## Profiling a run

```bash
./gradlew ":26.3.x:runBenchmark" -Pprofile -Pbenchmark=standard
```

`-Pprofile` records the run with JFR, an execution sample every millisecond, into
`run/<node>/benchmark/latest.jfr`. Read it with JDK Mission Control or `jfr print --events
jdk.ExecutionSample --stack-depth 64`; filter to stacks through `Guard.tick` to see where a guard's time
goes. A recorded run is slower and says so in `environment.profiling`: it is a recording, not a
measurement.

## What it measures

### Villages

`VillageTicker.tickLevel`, the mod's whole village tick, over a set of villages of the run's own: one
bell each, a chunk apart, buried in loaded chunks, nothing else around them (the roadmap's "empty
village"). They are built from saved data, so they sit closer than villages could merge-free in a real
world; spacing does not change what an update costs. The clock the scheduler reads is simulated, many
ticks to a server tick, so a run covers twenty update intervals in a second.

Per count: ms per tick (mean, p50, p95, p99, max), updates, microseconds per update, bytes allocated per
tick. A scenario that loses a village during the run (a bell the update could not find) fails rather than
report a smaller village count.

`unloadedVillages` is 50 villages far outside anything loaded: the cost of villages nobody is near.

### Memory

5 000 typical villages (3 bells, 20 members, 4 guards, 10 stockpile kinds) loaded into one set the way a
level holds them; the heap after a full collection, before and after, divided by their number, is what
each one keeps, its share of the chunk index included. Other threads allocate meanwhile, so a hundred
bytes either way is noise.

### Mobs

Real server ticks, around a bell in the middle of the area, at noon: guards on patrol, guards in combat
(zombies from the edge every five seconds, up to as many as there are guards), guards with nobody near,
and at the guard budget's count, 20 vanilla villagers and 20 vanilla iron golems in the same spot.

Nobody joins a benchmark, and a guard with no player within 48 blocks idles: no patrol, no looking around,
fewer scans, and while it stands still it thinks one tick in four. So the patrol and combat scenarios
tell every guard a player is near, through `PlayerPresence` and the dev mod's `CivVillagerMixin`, and measure a
guard someone watches; "guards, nobody near" tells them no, and measures the idle guard. Every kind is timed from the
outside, around each entity's whole tick, by `ServerLevelMixin` and `EntityTickTimer`: its AI, its
pathfinding, and the movement and collision vanilla does for any mob. That is stricter than timing the
mod's own goals, and it is what lets a guard be set beside a vanilla mob. Guards are also timed from the
inside by `GuardStats`; the two agree.

`scansPerGuardPer10Ticks` counts the guard's target scans: the budget is at most one per ten ticks, for
a guard someone watches. A scenario in which some mob did not tick on some tick fails rather than look
cheap.

The patrol and combat budgets are ratios: 20 guards against the 20 iron golems of the same run, so a slower
or busier machine moves both sides together and the verdict does not depend on it. The idle budget is in
milliseconds, since it is the one that has to stay small whatever a golem costs.

With one guard the per-guard figure is high: that guard's code is still being compiled by the JIT. Read
per-guard cost off the 20 and 50 guard rows.

## What it does not measure

- Client work: rendering the guard, its armor and held items.
- The wire: entity tracking and the packets a guard causes.
- A real village around the guards: beds, workstations and villagers are absent, so guards never sleep
  and villages never count members during the mob scenarios.
- The leak check of section 1.4 (open and close a singleplayer world, then a class histogram), which needs
  a client: `tools/agent/smoke/leak.jsonl` runs it.

## What it does to the world

Works in `civillagers-benchmark`, a world of its own. Force-loads 11x11 chunks around the first candidate
chunk, from (64, 64) outwards, that is not ocean, river or beach. Every block it places goes through
`BenchmarkWorld.place`, every entity it spawns through `track`, every bell it gives the level's own
villages through `addLevelAnchor`, and `close` undoes all three and unforces the chunks however the run
ends; only a crash mid-run can leave them behind. The guard scenarios set the time to noon and leave it.

## Rules

- A scenario that stops exercising the mod must fail, never report fast numbers.
- Stages do bounded work per call and resume on the next tick; never loop until done inside a stage.
- A budget figure the report marks belongs to docs/ROADMAP.md; change the limit there and in
  `BenchmarkRun` together.
