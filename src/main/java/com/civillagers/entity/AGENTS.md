# entity — the mod's villagers

| Where | What |
|---|---|
| `CivVillager` | The base every villager of the mod extends: look, voice, clothes, idling, saving, zombies, the level badge. [civ-villager.md](../../../../../../docs/knowledge/civ-villager.md) |
| `CivEntities` | Registration of every entity type and its spawn egg |
| `ZombieVillagers` | Turning into a vanilla zombie villager and the cure back. [zombie-guard.md](../../../../../../docs/knowledge/zombie-guard.md) |
| `guard/` | The guard: `Guard`, its gear by level (`GuardGear`), its trades (`GuardTrades`), its cost (`GuardStats`). [guard.md](../../../../../../docs/knowledge/guard.md) |

## Adding a role

The steps are in [civ-villager.md](../../../../../../docs/knowledge/civ-villager.md#adding-a-role). In this
tree: the role's classes in `entity/<role>/`, its goals in `ai/<role>/`, its registration in
`CivEntities`, its note in `docs/knowledge/`.

## Rules

- **Saved state goes through `saveExtra` / `loadExtra`** (`platform/SavingMob`), calling `super` first;
  every key has a fallback, so a world saved before the key existed still loads.
- **Synced state** (what a client draws: clothes, badge, head shake) is an `EntityDataAccessor` defined in
  the class that owns it; anything else stays on the server.
- **Never hold another entity** past a tick: store its `UUID`, look it up when needed.
- **`aiStep` runs every tick, at rest too**, before `CivVillager` skips three ticks in four; keep it to
  field reads and counters, and put anything that scans in a goal.
- What an addon may call is listed in [addon-api.md](../../../../../../docs/knowledge/addon-api.md): a
  public method there changes only with a line in `CHANGELOG.md`.
