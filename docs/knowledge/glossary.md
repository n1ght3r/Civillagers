# Glossary

The words the code, the docs and the lang files use. One word per concept: when a new one comes in, add
it here, and say why in [decisions.md](decisions.md) if it was a choice.

| Term | Meaning | In game (en / vi) | Note |
|---|---|---|---|
| villager of the mod, `CivVillager` | The base every role extends: guard, later chief and builder | | [civ-villager.md](civ-villager.md) |
| role | What a `CivVillager` does: its goals and state on top of the base | | [civ-villager.md](civ-villager.md) |
| combatant, civilian | A villager of the mod that fights (a guard with a weapon), or one that flees and hides | | [villager-life.md](villager-life.md) |
| alarm | A bell heard ringing within the last 15 s | | [villager-life.md](villager-life.md) |
| meeting hours | 9000 to 11000 ticks of the day, when villagers gather at the bell | | [villager-life.md](villager-life.md) |
| guard | The mod's villager who fights | Villager Guard / Lính gác | [guard.md](guard.md) |
| zombie guard | A zombie villager that was a guard, tagged `civillagers.zombie_of.civillagers.guard` | (a Zombie Villager) | [zombie-guard.md](zombie-guard.md) |
| addon API, hook, listener | `com.civillagers.api`: what other mods use; a hook is one `Listeners` list | | [addon-api.md](addon-api.md) |
| villager type | The biome a villager's clothes come from: `minecraft:plains`, `snow`, ... | | [villager-type.md](villager-type.md) |
| clothes | The `type/<type>.png` texture drawn over the bare villager body | | [villager-type.md](villager-type.md) |
| gear | What a guard holds and wears: weapon, shield, armor | | [guard.md](guard.md) |
| village | Saved data for one village, per dimension | | [village.md](village.md) |
| anchor, bell | The bells a village grows around | | [village.md](village.md) |
| member | A villager who belongs to a village, kept as a UUID | | [village.md](village.md) |
| chief | The village's decision maker (phase 3, not built yet) | | [docs/ROADMAP.md](../ROADMAP.md) |
| builder | Who expands the village (phase 4, not built yet) | | [docs/ROADMAP.md](../ROADMAP.md) |
| idle, at rest | A guard with no player within 48 blocks; at rest it also stands still with nothing to do | | [guard.md](guard.md) |
| node | One Gradle subproject: a Minecraft version on a loader, e.g. `26.3.x`, `1.21.1-neoforge` | | [AGENTS.md](../../AGENTS.md) |
| seam | The one place a loader or version difference lives: `Loader`, `Vanilla`, `ClientLoader`, `ClientVanilla` | | [platform/AGENTS.md](../../src/main/java/com/civillagers/platform/AGENTS.md) |
| budget | The most a feature may cost a server per tick | | [docs/ROADMAP.md](../ROADMAP.md) |
| agent client | A real client driven through a file queue, never the mouse | | [agent/AGENTS.md](../../src/dev/java/com/civillagers/dev/agent/AGENTS.md) |
