# mixin — changes to vanilla classes

The last resort, when no hook or subclass reaches what the mod needs. Each mixin says in its Javadoc what
it changes and why there was no other way.

| Mixin | What |
|---|---|
| `MobMixin` | Zombies and raiders get a target goal for guards (`ai/guard/GuardEnemies`) |
| `ZombieMixin`, `ZombieVillagerMixin` | A zombie turns a villager of the mod; the cure brings it back; a zombie villager's kept trades are read back |
| `BellBlockEntityMixin` | The mod's villagers hear a bell ring |
| `LivingEntityMixin` | A guard gains the experience of what it kills |

## Rules

- In `com.civillagers.mixin` (client ones in `com.civillagers.client.mixin`), package-private and
  `abstract`, every injected member and every added method prefixed `civ$`, listed in
  `civillagers.mixins.json` (or the client one), or it silently does nothing.
- `injectors.defaultRequire` is 1: an injection that finds no target fails the game at start, on every
  node. Run the gametests of every node a mixin touches.
- A mixin class cannot be named outside this package. To read something vanilla keeps private, the mixin
  implements an interface in `platform/` that common code casts to (`ZombieVillagerOffers`).
- This is one of the two places (with `platform/`) a Stonecutter `//?` block is allowed: a method whose
  signature differs per version gets one injection per version.
- Do the work in the mod's own classes and keep the mixin to the hook: it calls a method there and returns.
