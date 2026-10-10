# Zombie guard

A [guard](guard.md) a zombie kills can rise as a zombie, as a villager does, and be cured back. The
same works for any [CivVillager](civ-villager.md) whose type is registered for it: the guard is, and an
addon's villager can be (`CivillagersApi.turnsIntoZombie`).

## In game

- **Turning**: when a zombie (or a husk, drowned or other zombie kind, as vanilla does for villagers)
  kills a guard: never on Easy or Peaceful, half the time on Normal, always on Hard. The guard drops
  nothing; it rises in place.
- **The zombie** is vanilla's own **zombie villager**: it looks, fights, burns and is cured exactly like
  one. It keeps the guard's gear (and fights with its weapon), its name and its
  [clothes](villager-type.md), and never despawns, so gear a player gave is never lost. Killed, it drops
  that gear.
- **Cure**: vanilla's. Weakness (a splash potion), then a golden apple; it shakes for 3 to 5 minutes
  (faster near beds and iron bars) and comes back a **guard**, with its gear, name and clothes.
- Unlike a cured villager, a cured guard gives no trade discount and no "Zombie Doctor" advancement:
  those belong to a villager's gossip and trades, which a guard has none of.

## In the code

| What | Where |
|---|---|
| The rules: which types turn, odds, turning, curing | [entity/ZombieVillagers.java](../../src/main/java/com/civillagers/entity/ZombieVillagers.java) |
| A zombie kills a villager of the mod | `mixin/ZombieMixin`, at the head of `Zombie.killedEntity`, where vanilla turns villagers |
| A cure finishes | `mixin/ZombieVillagerMixin`, at the head of `ZombieVillager.finishConversion` |
| Conversion across versions | `Vanilla.convert`, `Vanilla.zombieVillagerType`, `Vanilla.villagerTypeOf`, `Vanilla.setVillagerType` |
| Hooks for addons | `CivVillagerEvents.TURNED_ZOMBIE`, `CivVillagerEvents.CURED` ([addon-api.md](addon-api.md)) |
| Tests | `aGuardTurnsWithAVillagersOdds`, `aGuardAZombieKillsOnHardRisesAsAZombie`, `aZombieGuardKeepsItsGearAndIsCuredBackToAGuard`, `anOrdinaryZombieVillagerIsLeftToVanilla` in [GuardGameTest.java](../../src/gametest/java/com/civillagers/gametest/GuardGameTest.java); `turningAndCuringCallTheirListeners` in [AddonApiGameTest.java](../../src/gametest/java/com/civillagers/gametest/AddonApiGameTest.java) |

**How a zombie remembers what it was**: an entity tag, the scoreboard tag `/tag` shows, naming the type:
`civillagers.zombie_of.civillagers.guard` for a guard (`ZombieVillagers.tagFor`). Vanilla saves it with
the entity on every version, so no data of the mod's own is needed. The cure removes it. A zombie
villager without one, or naming a type that is not registered, is cured by vanilla, into a villager.

A conversion makes a new entity with a new UUID. The [village](village.md) finds its guards again on its
next recount, so nothing has to be moved by hand.

## Related

- [guard.md](guard.md)
- [civ-villager.md](civ-villager.md)
- [villager-type.md](villager-type.md)
- [decisions.md](decisions.md): why a vanilla zombie villager rather than an entity of its own.
