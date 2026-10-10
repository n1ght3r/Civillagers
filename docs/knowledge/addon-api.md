# Addon API

What another mod builds on, in [com.civillagers.api](../../src/main/java/com/civillagers/api). It names
no mod loader, so an addon uses it the same way on Fabric and NeoForge. Changes to it go in the
changelog; the rest of the mod may change in any release.

## What there is

| Need | Use |
|---|---|
| Give guards (or any villager of the mod) more goals, or remove the mod's | `CivVillagerEvents.REGISTER_GOALS` |
| Decide what a guard goes after, unprovoked | `GuardEvents.IS_THREAT`, answering `Verdict.YES`, `NO` or `PASS` |
| Make a mod's weapon a guard's weapon | Add it to the item tag `civillagers:guard_weapons` (`CivTags.GUARD_WEAPONS`), from a datapack or the addon's data |
| React to a villager turning into a zombie, or being cured | `CivVillagerEvents.TURNED_ZOMBIE`, `CivVillagerEvents.CURED` |
| A villager of the addon's own | Extend [CivVillager](civ-villager.md); draw it with `CivVillagerRenderer` |
| Make it turn into a zombie villager and be cured back | `CivillagersApi.turnsIntoZombie(type)` |

Beside the `api` package, `CivVillager` (`villagerType`, `playerNear`, `isCombatant`, `raidCenter`,
`raidWon`, `alarmBell`, `refuse`, ...) and the public methods of `Guard` (`goesAfter`, `findThreat`,
`equipGear`, `takeGear`, `gearSlot`, ...) are kept stable for addons. A villager of an addon's own decides
whether it fights by overriding `isCombatant()`.

## Rules for listeners

- Register from the addon's initializer, while the game loads. `Listeners.register` keeps them in order;
  there is no unregistering.
- Listeners run on the server thread. `IS_THREAT` runs in every guard's target scan: keep it cheap and
  allocation free, and answer `PASS` for anything that is not the addon's business.
- The first listener that answers anything but `PASS` decides; the mod's own rule comes last.
- `REGISTER_GOALS` runs from the villager's constructor, before its own fields are set; add goals there,
  but read the villager's state from inside the goals, later.
- The performance rules in [AGENTS.md](../../AGENTS.md) hold for an addon's goals too: a goal that scans
  the world every tick undoes the guard's budget.

## Example

```java
public void onInitialize() {
    // Guards leave this addon's peaceful zombies alone.
    GuardEvents.IS_THREAT.register((guard, mob, ranged) ->
            mob instanceof PeacefulZombie ? Verdict.NO : Verdict.PASS);

    // Guards also tend to this addon's watchtowers.
    CivVillagerEvents.REGISTER_GOALS.register((villager, goals, targets) -> {
        if (villager instanceof Guard guard) goals.addGoal(6, new ManWatchtowerGoal(guard));
    });
}
```

A weapon joins from data, in the addon or a datapack: `data/civillagers/tags/item/guard_weapons.json`
with `"replace": false` and the item in `values` adds to the mod's tag rather than replacing it.

## In the code

- [api/](../../src/main/java/com/civillagers/api): `CivVillagerEvents`, `GuardEvents`, `CivTags`,
  `CivillagersApi`, `Listeners`, `Verdict`.
- Where they fire: `CivVillager.registerGoals`, `Guard.goesAfter`, `ZombieVillagers.zombify` and `cure`.
- The tag's contents: `CivItemTagProvider` in [src/datagen](../../src/datagen/java/AGENTS.md).
- Tests: [AddonApiGameTest.java](../../src/gametest/java/com/civillagers/gametest/AddonApiGameTest.java),
  used as an addon would.

## Related

- [civ-villager.md](civ-villager.md), [guard.md](guard.md), [zombie-guard.md](zombie-guard.md)
- [decisions.md](decisions.md): why plain listener lists, not a loader's event system.
