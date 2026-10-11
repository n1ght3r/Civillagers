# Villager life

What a [CivVillager](civ-villager.md), the guard among them, does the way a vanilla villager does, and
what it leaves out. Vanilla runs these on a villager's `Brain`; the mod writes each as a goal of its own,
cheaper and under the [performance rules](../../AGENTS.md), and never makes the guard a `Villager`
([decisions.md](decisions.md)).

## Civilian or combatant

`CivVillager.isCombatant()` splits the two. A **combatant** fights; a **civilian** behaves like a
villager. A builder or a chief will be a civilian; a [guard](guard.md) is a combatant **only while it
holds a weapon** (`civillagers:guard_weapons`). Take its weapon away and it lets go of its target, looks
for none, and flees and hides like any villager; being hurt still calls the guards near it.

## What they do

| Vanilla villager | The mod's villagers | Goal |
|---|---|---|
| Runs from zombies, vexes, zoglins and raiders that come close, and panics when hurt | Civilians the same, at vanilla's distances (8 to 12 blocks); combatants stand their ground | `CivPanicGoal` (1) |
| Hides at home when a bell rings, and during a raid | Civilians go to the nearest bed and stay there while the alarm (15 s) or the raid lasts | `CivHideGoal` (2) |
| (nothing: a villager hides) | An armed guard runs to the bell that rang and stands by it; during a raid it hunts the raiders instead | `GuardRallyGoal` (3), `GuardRaidGoal` (3) |
| Celebrates a raid won: jumps, cheers, fireworks | The same, until the raid is over | `CivCelebrateGoal` (3) |
| Gathers at the bell in the meeting hours (9000 to 11000) and gossips | Walks to a bell of its village now and then in those hours and turns to the one nearest it; each turns only its own head, so two side by side face each other and a third never pulls a head back and forth | `CivMeetGoal` (6) |
| Trades, levels up from novice to master, shows its level on its belt | A guard the same, with trades of its own, and it also levels up from what it kills ([guard.md](guard.md#levels)) | `Guard` (a `Merchant`), `GuardTradeGoal` (1) |
| Shakes its head and says no when it cannot trade | A guard while it fights, sleeps or trades with someone else | `CivVillager.refuse()` |
| Sleeps in a bed at night, wakes when hurt | Guards already did (`GuardSleepGoal`); vanilla wakes any sleeper that is hurt | |
| Opens doors, looks at players, looks around | Already in `CivVillager` | |

**Hearing the bell**: vanilla tells villagers through their brains, which the mod's villagers do not
use, so `mixin/BellBlockEntityMixin` hooks the ring (`BellBlockEntity.triggerEvent`, event 1) and calls
`CivVillager.bellRang`, which reaches every villager of the mod within 32 blocks. A ring is rare, so this
costs one box query per ring.

**Cost**: panic looks for danger at most every 10 ticks and only for civilians; hiding looks for a bed
every 40 ticks and only during an alarm or a raid; meeting decides every 200 ticks and only with a player
near; celebrating reads the raid lookup the villager already makes. Every walk is a path from the shared
budget (`GuardPathBudget`).

## Left out, and why

| Vanilla | Why not |
|---|---|
| Professions, job sites, working | A guard's job is the village's safety; a job site claimed by a guard is one a villager loses. It restocks on a timer instead of at a job site |
| Breeding | Undecided: more guards are to come from recruiting villagers ([docs/ROADMAP.md](../ROADMAP.md) 3.6), not from guard babies |
| Gossip and reputation | Reputation drives prices and iron golems; a guard's prices move only with demand, and golems are what guards replace |
| Summoning iron golems when panicking | Would double the village's defence on top of its guards |
| Gifts to the Hero of the Village | Gifts are a profession's (a farmer's bread, a smith's tools); a guard has none |
| Struck by lightning into a witch | A guard is not a villager to vanilla; turning it would lose its gear and its role |
| Picking up and sharing food, farming | A guard picks nothing up, so gear a player gives it is never swapped for something it finds |
| Babies playing | No guard babies |

## In the code

`entity/CivVillager` (`isCombatant`, `raidCenter`, `raidWon`, `hearBell`, `alarmBell`, `bellRang`,
`refuse`, `isUnhappy`), `entity/guard/Guard` (trading), the goals in [ai/](../../src/main/java/com/civillagers/ai) (`CivPanicGoal`,
`CivHideGoal`, `CivMeetGoal`, `CivCelebrateGoal`, `GuardRallyGoal`, `Homes`), `mixin/BellBlockEntityMixin`,
and the head shake in `client/render/CivVillagerModelLayers.shakeHead`. Tests:
[VillagerLifeGameTest.java](../../src/gametest/java/com/civillagers/gametest/VillagerLifeGameTest.java).

## Related

- [civ-villager.md](civ-villager.md), [guard.md](guard.md)
- [decisions.md](decisions.md): why goals of its own rather than extending `Villager`.
