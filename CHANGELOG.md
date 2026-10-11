# Changelog

## Unreleased

- Project structure: Fabric 26.3, 26.2 and 1.21.1, NeoForge 1.21.1, one source tree via Stonecutter.
- Village core: villages found from bells and beds, saved per dimension, merged and split as bells come
  and go, updated on a staggered, budgeted schedule; `/civillagers debug stats|here|scan`.
- Guards: an entity of their own that looks like a villager with two free arms. They patrol their village
  by day, sleep in a free bed at night, fight hostile mobs with a sword, an axe or a bow, and head for a
  raid. Hand a guard a weapon, a shield or armor by using it on the guard; sneak with an empty hand to take
  gear back. A Villager Guard Spawn Egg is in the Spawn Eggs tab.
- Guards take crossbows (they charge, aim and shoot, with the crossbow poses), raise their shield against
  archers and against a creeper about to blow up, and are hunted by zombies and raiders as villagers are.
- Guards are named Villager Guard, have a villager's 20 health, and wear the clothes of the biome they
  were born in, as villagers do.
- A guard a zombie kills can rise as a zombie villager that keeps its gear and clothes, with the odds a
  villager has (never on Easy, half the time on Normal, always on Hard). Cure it as a zombie villager,
  with weakness and a golden apple, and it comes back a guard.
- Guards live more like villagers: they meet the villagers at the bell in the late morning, celebrate a
  won raid with fireworks, and shake their heads at an empty hand. An armed guard runs to a bell that
  rings; a guard with no weapon fights nothing and flees from zombies and raiders and hides, as villagers
  do.
- For addons: `com.civillagers.api`, with hooks for guards' goals and targets and for turning into a zombie
  and being cured, the item tag `civillagers:guard_weapons` (what a guard takes as a weapon), and
  `CivVillager`, a base class for a villager of an addon's own. See `docs/knowledge/addon-api.md`.
- Guards nobody is near (48 blocks) idle: they stop patrolling, look for targets less often and think one
  tick in four while they stand still. Path searches in a fight are capped and only follow a target that
  moved.
- `runBenchmark`: what the mod costs a server, measured against the roadmap's budgets.
- Guards have a villager's five levels, Novice to Master, at vanilla's experience thresholds, with
  vanilla's badge on the belt. They gain experience by trading and from what they kill (the experience the
  mob would drop for a player). A level adds no stat: it upgrades the guard's own gear by table (leather
  up to diamond, a shield, enchantments at Expert and Master) and teaches two more trades. A spawn egg
  makes a guard of any level.
- Guards trade, as villagers do: bounties on monster drops and the gear of their rank, priced as vanilla's
  armorer, weaponsmith, cleric and fletcher price the same goods. Click a guard with anything but gear to
  trade.
- A guard's own gear never drops (from it or from its zombie) and never wears out; a piece a player hands
  over goes on top of it, always drops, and is the one piece the player can take back. A zombie guard keeps
  its level, experience and trades through the cure.
- Guards keep out of water: patrol and meeting points are on dry ground only, and paths go round water.
- Guards keep to their village, as iron golems do: they give up a chase far past its edge and walk back.
  In a raid they meet the raiders in the village, going first for those attacking villagers, instead of
  gathering at the bell or running out to the raid. Zombies and raiders go for a guard before a villager.
- Guards meeting at the bell no longer shake their heads back and forth.
- For addons: `Guard` moved to `com.civillagers.entity.guard`, and the goals to `com.civillagers.ai.guard`
  and `com.civillagers.ai.villager`.
