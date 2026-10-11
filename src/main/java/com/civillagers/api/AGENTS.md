# api — what addons build on

Everything here is used from other mods. What it offers and how is in
[addon-api.md](../../../../../../docs/knowledge/addon-api.md).

## Rules

- **Stable**: a change to anything public here, or to the public methods of `CivVillager` and
  `entity/guard/Guard` that addon-api.md lists, gets a line in `CHANGELOG.md`. Add, do not change: a new
  listener list or a new overload rather than a new parameter.
- **Names no loader**: listeners are plain Java (`Listeners`), registered from an addon's own initializer
  on either loader.
- **Cheap to fire**: a hook in a per-tick path (`GuardEvents.IS_THREAT`) runs in every guard's scan, so its
  listeners loop by index and allocate nothing; say so in the hook's Javadoc.
- Each hook gets a case in `AddonApiGameTest`.
