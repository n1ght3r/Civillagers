# Decisions

Choices made on purpose, newest last, each with its reason. Revisit one by writing the new decision
here and saying what it replaces; never just change the code against it.

## Naming

- **Roles are short nouns, without a `villager_` prefix**: `civillagers:guard`, later `chief`, `builder`.
  The namespace `civillagers` already says "villager", and vanilla names roles the same way
  (`iron_golem`, `pillager`, `wandering_trader`). Class names follow: `Guard`, `GuardMeleeGoal`.
- **One word per concept, everywhere**: code, ids, docs and lang say "guard". The player-facing name is
  free to say more: **Villager Guard** in `en_us`, so it reads apart from other mods' "Guard" (Guard
  Villagers, for one); `Lính gác` in `vi_vn`.
- Terms are listed in [glossary.md](glossary.md).

## Guard

- **Its own entity, not a villager profession** (phase 2): a profession only changes trades, and a
  villager's `Brain` AI costs more and is harder to control than a `GoalSelector`.
- **A villager's 20 health** (was 30): guards felt too tough beside villagers. Armor, the shield and
  regaining health out of combat still make an equipped guard sturdier.
- **Clothes from the biome**, drawn from vanilla's own textures read by path: guards look like the
  villagers of their village. See [villager-type.md](villager-type.md).
- **Villager type stored as an id string**: reads the same on 1.21.1 and 26.x, where the registry
  differs.

## Structure and addons

- **One base class, `CivVillager`, for every villager of the mod** (guard, later chief and builder):
  look, voice, clothes, idling and saving are written once, and a role only adds its goals and state.
  The base registers the shared goals in a `final` `registerGoals` and calls the role's
  `registerRoleGoals`, so a role cannot skip the shared goals or the addon hook by accident.
- **A loader-free addon API** in `com.civillagers.api`: plain listener lists (`Listeners`) instead of
  Fabric's `Event` or NeoForge's bus, since common code never names a loader and an addon on either
  loader then writes the same code. Fired by index, so a hook in a hot path allocates nothing.
- **Data before code where it fits**: what a guard takes as a weapon is the item tag
  `civillagers:guard_weapons`, so a datapack can change it without any code.
- **Only registered types turn into zombies**: the type a zombie is cured into is named in its tag and
  must be registered (`ZombieVillagers.register`), so a hand-written tag can never make the cure create
  something that is not a villager of the mod.

- **Villager behaviour as goals of its own, not by extending `Villager`**: `Villager` runs on a `Brain`
  that would fight the guard's goals for control (it flees zombies a guard should fight), vanilla treats
  anything that is a `Villager` as one in hundreds of places (trades, job sites, witches, golems), its
  `Brain` costs more than the budget allows, and its internals differ most between 1.21.1 and 26.x. Each
  behaviour that fits is a cheap goal on `CivVillager` instead. See [villager-life.md](villager-life.md).
- **A guard is a combatant only while armed**: a disarmed guard behaves as a villager does, so taking its
  weapon is how a player stands it down, and it is never a fighter with its fists.

## Zombie guard

- **A vanilla zombie villager with an entity tag**, not a zombie guard entity of its own: vanilla's
  look, sounds, cure and its rules all come for free, the gear shows on the zombie, and no model,
  renderer or save data is needed. The tag (`civillagers.zombie_of.<namespace>.<path>`) is saved by
  vanilla on every version. See [zombie-guard.md](zombie-guard.md).
- **Vanilla's odds** for a villager (Easy never, Normal half, Hard always), so a guard is no safer or
  weaker than the villagers it guards.
- **The zombie never despawns**: it carries gear a player may have given.

## Related

- [guard.md](guard.md), [villager-type.md](villager-type.md), [zombie-guard.md](zombie-guard.md)
- [docs/ROADMAP.md](../ROADMAP.md), "Open questions": decisions still to make.
