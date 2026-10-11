# CivVillager

The base class of every villager of the mod: the [guard](guard.md) today, the chief and builder later,
and an addon's own. A role extends it and adds only what makes it that role.

## What every one shares

| What | How |
|---|---|
| Pace | A villager's speed (0.5), each with its own pace up to 5% either way, drawn once and saved (`speedVariant`) |
| Look and voice | Villager sounds, a villager's 20 health (`createVillagerAttributes()`), opens doors, picks nothing up, never despawns |
| Clothes | Its [villager type](villager-type.md), from the biome it spawns in; saved, synced, drawn by `CivVillagerRenderer` |
| Villager life | Civilians flee and hide, an armed guard rallies, all meet at the bell, celebrate and shake their heads: [villager-life.md](villager-life.md). `isCombatant()` says which |
| Idling | No player within 48 blocks: the look-around goals stop, and at rest (`resting()`) the AI and movement run one tick in four. A role reads `playerNear()` to idle its own goals |
| Goals | Floating (0), panic (1), hiding (2), celebrating (3), doors (5), meeting (6), looking around (7, 8); the role's in `registerRoleGoals()`; then addons' through `CivVillagerEvents.REGISTER_GOALS` |
| Raids and the bell | `raidCenter()`, `raidWon()` (one lookup every 40 ticks), `alarmBell()` (a bell heard in the last 15 s) |
| Saving | `saveExtra` / `loadExtra`, through [platform/SavingMob](../../src/main/java/com/civillagers/platform/SavingMob.java): strings, ints, longs and anything with a codec; a subclass calls `super` first |
| Zombies | A type registered with `ZombieVillagers.register` turns and is cured back: see [zombie-guard.md](zombie-guard.md). What else it keeps goes through `carryIntoZombie` / `carryFromZombie` |
| Level badge | `villagerLevel()`: 1 to 5 draws vanilla's badge on the belt, 0 (the default) none. A guard has one |
| Model and renderer | `CivVillagerModel` and `CivVillagerRenderer<T>`: a villager's head and robe on a humanoid with free arms, with clothes, the level badge, held items and armor |

## Adding a role

1. A class extending `CivVillager`, with a `(EntityType, Level)` constructor, `registerRoleGoals()`, and
   its own state saved through `saveExtra` / `loadExtra` (calling `super`).
2. Attributes from `createVillagerAttributes()`, adding what the role needs.
3. Registration in `CivEntities` (`Loader.onRegister`, `Loader.registerAttributes`), and
   `ZombieVillagers.register` if it turns into a zombie.
4. The renderer: `ClientLoader.registerEntityRenderer(type, CivVillagerRenderer::new)`, or a subclass for
   layers of its own.
5. Gametests, a knowledge note, and the budget in [docs/ROADMAP.md](../ROADMAP.md).

An addon does the same with its own loader's registration; see [addon-api.md](addon-api.md).

## In the code

[entity/CivVillager.java](../../src/main/java/com/civillagers/entity/CivVillager.java),
[client/platform/CivVillagerRenderer.java](../../src/client/java/com/civillagers/client/platform/CivVillagerRenderer.java),
`client/platform/CivVillagerModel`, `client/render/CivVillagerModelLayers`.

## Related

- [villager-life.md](villager-life.md): the villager behaviour it gives every role.
- [guard.md](guard.md): the one role so far.
- [addon-api.md](addon-api.md): extending it from another mod.
- [decisions.md](decisions.md): why one base class.
