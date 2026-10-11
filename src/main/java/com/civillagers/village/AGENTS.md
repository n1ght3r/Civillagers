# village — the village as saved data

One concept: villages found from bells and beds, saved per dimension, ticked on a budget. What a village
is and how it behaves is in [village.md](../../../../../../docs/knowledge/village.md).

| Where | What |
|---|---|
| `Villages` | The per-level `SavedData`: every village of a dimension, found, merged and split |
| `Village` | One village: the bells it grew around (anchors), its villagers and guards by UUID, its level and stockpile |
| `VillageDetector`, `VillageClusters` | Finding villages from bells and beds, and grouping bells into one |
| `VillageIndex` | Which village a chunk belongs to, for a lookup without a scan |
| `VillageScheduler`, `VillageTicker` | Staggered, budgeted ticking of loaded villages only |
| `VillageStats`, `StockpileKeys`, `UuidList` | What village work cost, for `/civillagers debug stats`, in a ring buffer; one shared instance per stockpile item id; UUIDs packed into longs, capped |

## Rules

- **Only UUIDs and positions** in a village, never an entity, a level or a chunk.
- **Every field is saved with the codec** and read back with a fallback; a new field gets a case in
  `VillageDataGameTest` (save and reload).
- **Work only in loaded chunks**, through `VillageScheduler`, within its per-tick time budget; never load or
  force a chunk.
- **Every cache here has a clear** in `Civillagers.clearCaches`, which runs on `Loader.onServerStopped`,
  added in the same change.
