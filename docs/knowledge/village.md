# Village

A village as the mod sees it: saved data per dimension, not an entity. It outlives the chunks its
villagers walk in.

## What it is

- **Anchored by bells** (vanilla's meeting point). A bell with a bed within the link distance founds a
  village or joins the one it links to. Bells close to each other are one village: a new bell can merge
  villages, a removed one can split a village.
- **Center and radius** follow from the bells alone, never from which chunks are loaded.
- **Holds**: its bells, members (villagers) and [guards](guard.md) as UUIDs, counts of beds and
  workstations, a level, a stockpile, the founding tick. Every collection is capped.
- **Members and guards are found** by the village's own update, which recounts them in turn with its
  other slow checks. A guard that turns into a [zombie](zombie-guard.md) and back gets a new UUID; the
  next recount picks it up.
- Later phases add a chief, needs and a build plan here: [docs/ROADMAP.md](../ROADMAP.md), phases 3 and 4.

## How it runs

- One `Villages` per level, in the level's `SavedData`; nothing in a static.
- `VillageScheduler` updates each village every few seconds, staggered across ticks, within a per-tick
  time budget, and only when its center chunk is loaded.
- `VillageDetector` reads points of interest from loaded chunks only: no chunk is loaded for it.
- `/civillagers debug stats|here|scan` show what villages hold and what they cost.

## In the code

[village/](../../src/main/java/com/civillagers/village): `Village` (one village), `Villages` (all of a
level, merge and split), `VillageDetector`, `VillageClusters`, `VillageIndex` (chunk to village),
`VillageScheduler`, `VillageTicker`, `UuidList`, `StockpileKeys`, `VillageStats`. Tests: the
`Village*GameTest` classes in [src/gametest](../../src/gametest/java/com/civillagers/gametest).

## Related

- [guard.md](guard.md)
- [AGENTS.md](../../AGENTS.md), "Performance and memory": the rules every village change keeps.
