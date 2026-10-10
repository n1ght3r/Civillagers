# Villager type

The biome a villager belongs to, which decides its clothes: `minecraft:plains` (brown robe),
`desert`, `jungle`, `savanna`, `snow`, `swamp`, `taiga`, or another mod's. Vanilla gives a villager
the type of the biome it is born in.

## How a villager of the mod wears it

- Every [CivVillager](civ-villager.md), the [guard](guard.md) among them, keeps the **id** of its type
  as a string (`CivVillager.villagerType()`), taken from the biome where it spawns. A string, not a
  registry entry, because the registry is shaped differently on 1.21.1 and 26.x and a string reads the
  same on both.
- Saved under the key `villager_type` through [SavingMob](../../src/main/java/com/civillagers/platform/SavingMob.java),
  and synced to clients as entity data. One saved without it, or given something that is not an id, wears
  `minecraft:plains`.
- A [zombie guard](zombie-guard.md) carries the type in its own villager data, so the cure gives it back.
- `Vanilla.villagerTypeAt(level, pos)` reads the type for a biome; `Vanilla.villagerTypeOf` and
  `Vanilla.setVillagerType` read and write a zombie villager's.

## How it is drawn

Vanilla draws a villager in layers: `textures/entity/villager/villager.png` (bare body, grey shirt), then
`type/<type>.png` (the clothes), then the profession. A villager of the mod draws the first two on its
own model:

- `CivVillagerModelLayers.TEXTURE` is the bare body.
- `CivVillagerRenderer`'s clothes layer draws `<namespace>:textures/entity/villager/type/<path>.png` over
  it. `CivVillagerModelLayers.clothes(type)` returns it, with vanilla's seven made once so drawing
  allocates nothing. Another mod's type uses the same path in its own namespace.
- The textures are vanilla's, read by path from the game, never copied into the mod.
- The model uses the villager texture's own UV layout, arms included, so the clothes land where they
  should. The robe (and the clothes on it) hides under leggings.

## Related

- [civ-villager.md](civ-villager.md)
- [guard.md](guard.md)
- [zombie-guard.md](zombie-guard.md)
- [platform/AGENTS.md](../../src/main/java/com/civillagers/platform/AGENTS.md): why the type goes through `Vanilla`.
