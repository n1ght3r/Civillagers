# client — how the mod's villagers look

Client-only code: everything that touches `net.minecraft.client`. It never goes in `src/main/java`;
NeoForge compiles both together, so only the Fabric nodes catch a mistake.

| Where | What |
|---|---|
| `CivillagersClient` | The client entrypoint both loaders call: renderers and model layers |
| `render/CivVillagerModelLayers` | What every villager of the mod looks like, the same on every version: the body mesh, clothes and badge textures, arm poses, the head shake |
| `platform/CivVillagerModel`, `CivVillagerRenderer` | The model and renderer, one class per side of 1.21.2's render states, with the clothes, badge and armor layers |
| `platform/ClientVanilla`, `ClientLoader` | The client's version and loader seams |

## Rules

- **Vanilla's textures are read by path, never copied** into the mod: the villager body, the clothes of
  each villager type, the level badges.
- **What differs per version stays in `platform/`** (`//?` blocks); what does not goes in `render/`.
- **Nothing allocates per frame**: textures are made once (`CivVillagerModelLayers`), render states carry
  what was read in `extractRenderState`.
- **Check the look in a real client** with the agent (`tools/agent/shots/guards.jsonl`), on every client
  node a change touches; a driven client never takes the mouse.
