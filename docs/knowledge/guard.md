# Guard

A villager who defends the village. Shown as **Villager Guard** (`Lính gác` in Vietnamese), registered as
`civillagers:guard`. Its own entity, not a villager profession: see [decisions.md](decisions.md). It is a
[CivVillager](civ-villager.md): its look, voice, clothes, idling and saving come from there, and this note
covers only what makes it a guard.

## In game

- **Looks** like a villager (head, nose, robe) with two free arms like a player's, so it shows the weapon,
  the shield and the armor it carries. It wears the clothes of its [villager type](villager-type.md).
- **Stats**: 20 health, as a villager has. Regains 1 health every 4 s (80 ticks) while it has no target.
  Base attack 1; the weapon adds its own damage, as for a player.
- **Spawns** from the Villager Guard Spawn Egg or `/summon`, with an iron sword and, one time in four, a
  shield. Never despawns. Counts as MISC, like villagers, so it never fills the monster cap.
- **Gear**: a player uses a weapon (`civillagers:guard_weapons`: swords, axes, bows, crossbows), a
  shield or an armor piece on it to hand it over (getting the old one back); sneaking with an empty hand
  takes gear back, one piece per use. Gear a player gave always drops when it dies. Bows and crossbows
  need no arrows.
- **Day**: patrols the village it stands in. **Night**: sleeps in a free bed. **Raid**: heads for it.
- **Fights** hostile mobs it sees within 16 blocks, and whatever hurts it; calls nearby guards when hurt.
  Leaves neutral mobs alone, and creepers to guards with a bow or crossbow; an addon can change that
  (`GuardEvents.IS_THREAT`). Raises its shield against archers and against a creeper about to blow.
- **Is hunted** by zombies and fighting raiders, as villagers are. A zombie that kills it may turn it into
  a [zombie guard](zombie-guard.md).
- **Idles** with no player within 48 blocks: no patrol, no looking around, fewer target scans, and at
  rest its AI and movement run one tick in four. Budgets: [docs/ROADMAP.md](../ROADMAP.md), phase 2.

## In the code

| What | Where |
|---|---|
| The role: goals, threats, raids, weapons, gear, regaining health | [entity/Guard.java](../../src/main/java/com/civillagers/entity/Guard.java) |
| What it shares with every villager of the mod | [entity/CivVillager.java](../../src/main/java/com/civillagers/entity/CivVillager.java), see [civ-villager.md](civ-villager.md) |
| Registration, spawn egg | [entity/CivEntities.java](../../src/main/java/com/civillagers/entity/CivEntities.java) |
| Goals: patrol, sleep, raid, target scan, melee, bow, crossbow, shield | [ai/](../../src/main/java/com/civillagers/ai) |
| Path budget in a fight | `ai/GuardCombatPath`, `ai/GuardPathBudget` |
| Who hunts guards | `ai/GuardEnemies`, called from `mixin/MobMixin` |
| Cost per tick, for the benchmark | `entity/GuardStats` |
| Model, renderer, clothes layer (shared) | `client/render/CivVillagerModelLayers`, `client/platform/CivVillagerModel`, `client/platform/CivVillagerRenderer` |
| Hooks for addons | `api/GuardEvents`, `api/CivTags`, see [addon-api.md](addon-api.md) |
| Tests | [GuardGameTest.java](../../src/gametest/java/com/civillagers/gametest/GuardGameTest.java), `tools/agent/shots/guards.jsonl` |

## Related

- [civ-villager.md](civ-villager.md): what it is built on.
- [addon-api.md](addon-api.md): what other mods can change.
- [villager-type.md](villager-type.md): its clothes.
- [zombie-guard.md](zombie-guard.md): what a zombie does to it, and the cure.
- [village.md](village.md): the village counts it by UUID.
- [decisions.md](decisions.md): own entity, name, health.
