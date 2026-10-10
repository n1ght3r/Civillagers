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
- Guards nobody is near (48 blocks) idle: they stop patrolling, look for targets less often and think one
  tick in four while they stand still. Path searches in a fight are capped and only follow a target that
  moved.
- `runBenchmark`: what the mod costs a server, measured against the roadmap's budgets.
