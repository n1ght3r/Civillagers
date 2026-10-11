# ai — goals

What the mod's villagers do, one `Goal` per behaviour on vanilla's `GoalSelector`, never a `Brain`
([decisions.md](../../../../../../docs/knowledge/decisions.md)).

| Package | What |
|---|---|
| `ai/` | What every goal leans on: `GuardPathBudget` (new paths per server tick), `Homes` (beds, from vanilla's points of interest), `Ground` (where a villager may stand) |
| `ai/villager/` | What every villager of the mod does as a villager does (`Civ*Goal`): panic, hide, meet, celebrate. [villager-life.md](../../../../../../docs/knowledge/villager-life.md) |
| `ai/guard/` | A guard's own (`Guard*`): patrol, sleep, raid, rally, target scan, changing target, being hurt, melee, bow, crossbow, shield, trading; its chase paths and swimming (`GuardCombatPath`) and who hunts guards (`GuardEnemies`). [guard.md](../../../../../../docs/knowledge/guard.md) |

A goal for a new role goes in a package of its own beside `guard/`; one every role shares goes in
`villager/`; a helper two packages need goes here, never in one of them (a villager goal never imports a
guard's).

## Rules for every goal

- **Rate-limit what scans**: a look at entities, blocks or points of interest runs at most every N ticks
  (a constant named `*_INTERVAL`), in a bounded box, and never while nothing could come of it (check the
  cheap conditions first: a field read before a box query).
- **Every new path comes from `GuardPathBudget`**: `tryTake` for an unhurried walk, `tryTakeCombat` for a
  chase. Refused, try again on a later tick. Search again only when the last path ended or the goal moved.
- **Walk only to dry ground in a loaded chunk**: points from the heightmap go through
  `Ground.onDryGround`; check `level.hasChunk` before reading a block.
- **No allocation per tick**: reuse a `BlockPos.MutableBlockPos`, no streams or capturing lambdas in
  `canUse` or `tick`. A goal may hold an entity while it runs (as vanilla's do) and drops it in `stop`;
  nothing outlives that.
- **Idle with nobody near**: a goal that is only for players to see (looking around, patrolling, meeting)
  checks `playerNear()`.
- **Turn only your own head**: a goal sets the look of the mob it runs on, never another's; two goals
  pulling one head make it swing every tick.
- A goal that every role should get is added in `CivVillager.registerGoals`; a role's in its own
  `registerRoleGoals`. Priorities taken are listed in `CivVillager`'s Javadoc; keep them there.
- A new goal gets a gametest, and its cost shows in `runBenchmark` (docs/ROADMAP.md, section 1).
