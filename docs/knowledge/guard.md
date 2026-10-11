# Guard

A villager who defends the village. Shown as **Villager Guard** (`Lính gác` in Vietnamese), registered as
`civillagers:guard`. Its own entity, not a villager profession: see [decisions.md](decisions.md). It is a
[CivVillager](civ-villager.md): its look, voice, clothes, idling and saving come from there, and this note
covers only what makes it a guard.

## In game

- **Looks** like a villager (head, nose, robe) with two free arms like a player's, so it shows the weapon,
  the shield and the armor it carries. It wears the clothes of its [villager type](villager-type.md), and
  the badge of its [level](#levels) on its belt, as a villager does.
- **Stats**: 20 health, as a villager has, at every level. Regains 1 health every 4 s (80 ticks) while it
  has no target. Base attack 1; the weapon adds its own damage, as for a player.
- **Spawns** from the Villager Guard Spawn Egg at a random level, 1 to 5, with that level's gear, trades
  and experience; `/summon` makes a novice. Never despawns. Counts as MISC, like villagers, so it never
  fills the monster cap.
- **Gear** is of two kinds. *Its own*: what its level gives it ([below](#gear-by-level)); it never drops
  (from the guard or its [zombie](zombie-guard.md)), never wears out (unbreakable), and nobody takes it
  off. *A player's*: a player uses a weapon (`civillagers:guard_weapons`: swords, axes, bows, crossbows),
  a shield or an armor piece on it to hand it over; it goes on top of the guard's own piece, which waits
  under it and keeps upgrading. Sneaking with an empty hand takes a player's piece back (one per use) and
  puts the guard's own back on. A player's piece always drops when it dies. Bows and crossbows need no
  arrows.
- **Trades**: anything else in the hand, an empty one included, opens its trades, as a villager's
  ([below](#trades)). It shakes its head instead while it fights, sleeps or trades with someone else, and
  drops a trade to fight.
- **Day**: patrols the village it stands in, on dry ground only, and in the late morning meets the
  villagers at the bell. Paths avoid water (a step into it costs twice vanilla's), so it takes bridges.
  **Night**: sleeps in a free bed. **Bell**: runs to it, outside a raid. See
  [villager-life.md](villager-life.md).
- **Raid**: each armed guard goes for the raider nearest it, within 96 blocks: one within 24 blocks is its
  target at once, seen or not, and it walks towards one farther off. Raiders come in at the edge of the
  village and go from door to door, so the guards meet them there and spread over them instead of
  gathering by the bell, the raid's center. With no raider in reach it waits near the center. It
  celebrates when the raid is won.
- **Unarmed**: with no weapon it is a civilian: it fights nothing, runs from zombies and raiders, and
  hides when the bell rings.
- **Fights** hostile mobs it sees within 16 blocks, and whatever hurts it; calls nearby guards when hurt.
  Leaves neutral mobs alone, and creepers to guards with a bow or crossbow; an addon can change that
  (`GuardEvents.IS_THREAT`). Raises its shield against archers and against a creeper about to blow.
- **Is hunted** by zombies and fighting raiders, as villagers are. A zombie that kills it may turn it into
  a [zombie guard](zombie-guard.md).
- **Idles** with no player within 48 blocks: no patrol, no looking around, fewer target scans, and at
  rest its AI and movement run one tick in four. Budgets: [docs/ROADMAP.md](../ROADMAP.md), phase 2.

## Levels

A villager's five levels and vanilla's thresholds of total experience; each shows as vanilla's badge.

| Level | Badge | Experience | Trades known |
|---|---|---|---|
| 1 Novice | stone | from the start | 2 |
| 2 Apprentice | iron | 10 | 4 |
| 3 Journeyman | gold | 70 | 6 |
| 4 Expert | emerald | 150 | 8 |
| 5 Master | diamond | 250 | 10 |

It gains experience two ways:

- **Trading**: each trade gives the guard the trade's experience, as a villager's does, and the player a
  few orbs at their feet.
- **Fighting**: a mob a guard kills gives it, at once, the experience it would drop for a player (5 for
  a zombie, 20 for a ravager, as vanilla counts it, gear and the weapon's enchantments included). No
  orbs drop for the guard, so it never has to walk after them; what drops for a player who hurt the mob
  too is vanilla's, as ever.

Once it has enough, it takes the level 2 s later, when nobody is trading with it (vanilla's delay): its
gear upgrades, it learns two trades, and it gets vanilla's 10 s of regeneration and happy particles.
**A level adds no stat**; the gear is what makes a higher guard stronger.

### Gear by level

Rolled from these tables, in percent, for a new guard of that level:

| Level | Helmet | Chestplate | Leggings | Boots | Shield | Armor: leather / chain / iron / diamond | Sword: wood / stone / iron / diamond | Armor enchanted |
|---|---|---|---|---|---|---|---|---|
| 1 | 40 | 0 | 0 | 100 | 0 | 100 / 0 / 0 / 0 | 70 / 30 / 0 / 0 | 0 |
| 2 | 75 | 45 | 30 | 100 | 20 | 40 / 60 / 0 / 0 | 0 / 80 / 20 / 0 | 0 |
| 3 | 100 | 85 | 75 | 100 | 50 | 0 / 35 / 65 / 0 | 0 / 0 / 100 / 0 | 0 |
| 4 | 100 | 100 | 100 | 100 | 75 | 0 / 0 / 100 / 0 | 0 / 0 / 90 / 10 | 20 |
| 5 | 100 | 100 | 100 | 100 | 95 | 0 / 0 / 75 / 25 | 0 / 0 / 60 / 40 | 50 |

A guard that **levels up** does not roll again: it upgrades what it has, so a piece is never lost,
swapped for a worse one or stripped of its enchantments, and the odds above still hold at every level.
An empty slot fills with the chance it gains between the two levels; a material keeps its place in the
old column and reads off the new one there, which only moves up because every column is at least as good
as the one before. Enchantments are vanilla's for a mob's spawn gear, at 5 to 15 levels for an expert and
15 to 30 for a master. See `GuardGear` for the arithmetic.

### Trades

Two drawn from each level's pool as it reaches the level, as a villager's. Every number is vanilla's
where vanilla has the same trade (26.3's `data/minecraft/villager_trade`): price, uses, experience, and
the price multiplier (0.2 for armor, weapons and the bell, 0.05 for the rest, bows and arrows included).
An enchanted item costs its base price plus its enchantment level (5 to 19), at most 64. A trade vanilla
has not is priced like its nearest one. It restocks at most every half day (12000 ticks), once a trade
was used; heavy use raises prices, as vanilla's demand does.

| Level | Buys (1 emerald each) | Sells | Vanilla it follows |
|---|---|---|---|
| Novice | 32 rotten flesh; 32 bones | iron sword (2) | cleric's rotten flesh; bones as rotten flesh (one a kill); a sword one ingot cheaper than the weaponsmith's axe (3) |
| Apprentice | 14 string | 16 arrows (1); chainmail chestplate (4); chainmail leggings (3); bell (36) | fletcher, armorer, the smiths' bell |
| Journeyman | 4 iron ingots; 4 gunpowder | shield (5) | the smiths' iron, armorer's shield; gunpowder as the cleric's 2 rabbit feet, a bit commoner |
| Expert | 1 diamond; 2 ender pearls | enchanted iron chestplate (9+); enchanted crossbow (3+) | the smiths' diamond, fletcher's crossbow; pearls as rabbit feet; the chestplate at the armorer's plain price |
| Master | | enchanted diamond sword (8+); chestplate (16+); helmet (8+) | weaponsmith, armorer |

The buys are bounties on what the monsters it fights drop; the sells are the gear of its rank.

## In the code

| What | Where |
|---|---|
| The role: goals, threats, raids, weapons, gear, levels, trading, regaining health | [entity/guard/Guard.java](../../src/main/java/com/civillagers/entity/guard/Guard.java) |
| Gear tables, rolling and upgrading | [entity/guard/GuardGear.java](../../src/main/java/com/civillagers/entity/guard/GuardGear.java) |
| Trade pools | [entity/guard/GuardTrades.java](../../src/main/java/com/civillagers/entity/guard/GuardTrades.java) |
| What it shares with every villager of the mod | [entity/CivVillager.java](../../src/main/java/com/civillagers/entity/CivVillager.java), see [civ-villager.md](civ-villager.md) |
| Registration, spawn egg | [entity/CivEntities.java](../../src/main/java/com/civillagers/entity/CivEntities.java) |
| Goals: patrol, sleep, raid (hunting the raiders), target scan, melee, bow, crossbow, shield, trading | [ai/](../../src/main/java/com/civillagers/ai) |
| Experience from a guard's kill | `mixin/LivingEntityMixin`, at the head of `dropExperience`, which every death goes through |
| Path budget in a fight | `ai/guard/GuardCombatPath`, `ai/GuardPathBudget` |
| Who hunts guards | `ai/guard/GuardEnemies`, called from `mixin/MobMixin` |
| Cost per tick, for the benchmark | `entity/guard/GuardStats` |
| Model, renderer, clothes and badge layers (shared) | `client/render/CivVillagerModelLayers`, `client/platform/CivVillagerModel`, `client/platform/CivVillagerRenderer` |
| Hooks for addons | `api/GuardEvents`, `api/CivTags`, see [addon-api.md](addon-api.md) |
| Tests | [GuardGameTest.java](../../src/gametest/java/com/civillagers/gametest/GuardGameTest.java), [GuardLevelGameTest.java](../../src/gametest/java/com/civillagers/gametest/GuardLevelGameTest.java), `tools/agent/shots/guards.jsonl` |

**Which gear is whose** is the slot's drop chance: 0 for the guard's own, 2 (always drops) for a
player's. Vanilla saves it and a zombie villager keeps it through turning and the cure, so the guard
never needs a list of its own. Its own piece under a player's is saved by the guard (`own_<slot>`).

**Why experience at the kill rather than orbs to pick up**: a guard walking after orbs was tried first;
it left orbs behind in a fight with several mobs and cost scans and paths. The experience is the same
number the orbs would have held, so a guard levels at the same pace either way.

## Related

- [civ-villager.md](civ-villager.md): what it is built on.
- [villager-life.md](villager-life.md): what it does as a villager does.
- [addon-api.md](addon-api.md): what other mods can change.
- [villager-type.md](villager-type.md): its clothes.
- [zombie-guard.md](zombie-guard.md): what a zombie does to it, and the cure.
- [village.md](village.md): the village counts it by UUID.
- [decisions.md](decisions.md): own entity, name, health.
