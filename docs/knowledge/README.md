# Knowledge graph

<!-- AGENTS.md imports this file, so it loads in every session: keep it a short map. -->

What the mod *is*, one concept per note, linked to each other and to the code. The `AGENTS.md` files say
how to work in an area (build, test, seams); these notes say what the things in the game are, how they
behave and why they were made that way. Read this map first, then follow links to the notes a task touches.

## How it works

- **A note is a node**: one concept, in its own file here, a page or two at most.
- **A link is an edge**: a relative Markdown link to another note, an `AGENTS.md` or a source file. Every
  note ends with a **Related** list of the notes it leans on.
- **Each note says**: what the thing is, how it behaves in game, where it lives in the code, and the
  decisions behind it, each with its reason.
- **Code wins.** A note that disagrees with the code is stale: fix the note in the same change. A change
  that adds or changes a concept updates its note, or adds one and lists it below.
- No copies: what an `AGENTS.md` or [docs/ROADMAP.md](../ROADMAP.md) already says is linked, not repeated.

## The map

```
                    ┌──────────── village ────────────┐
                    │ members, guards (UUIDs), bells  │
                    └───────────────┬─────────────────┘
                                    │ counts
 villager-type ── wears ──►   civ-villager   ◄── extends ── addon-api
   (biome clothes)                  │ is a
                                    ▼
                                  guard       ◄── hunted by ── zombies, raiders
                                    │ killed by a zombie / cured
                                    ▼
                              zombie-guard
```

| Note | What |
|---|---|
| [civ-villager.md](civ-villager.md) | The base class every villager of the mod shares, and how to add a role |
| [guard.md](guard.md) | The guard: a villager who fights. Stats, gear, AI, idling, who hunts it |
| [villager-type.md](villager-type.md) | The biome a villager's clothes come from, and how a guard wears them |
| [zombie-guard.md](zombie-guard.md) | A guard a zombie kills rises as a zombie villager; cured, it is a guard again |
| [village.md](village.md) | The village as saved data: bells, members, guards, scheduling |
| [addon-api.md](addon-api.md) | What another mod builds on: events, tags, registrations |
| [decisions.md](decisions.md) | Design decisions and naming rules, with their reasons |
| [glossary.md](glossary.md) | The words the code, the docs and the lang files use, in English and Vietnamese |

## The rest of the graph

- [AGENTS.md](../../AGENTS.md): build, constraints, where code goes.
- [docs/ROADMAP.md](../ROADMAP.md): phases, what is done, performance budgets.
- [platform/AGENTS.md](../../src/main/java/com/civillagers/platform/AGENTS.md): loader and version seams.
- [src/gametest/java/AGENTS.md](../../src/gametest/java/AGENTS.md): gametests.
- [src/dev/java/AGENTS.md](../../src/dev/java/AGENTS.md): showcase, benchmark, agent client.
