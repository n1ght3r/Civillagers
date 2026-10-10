# platform — the seams

Two kinds of difference stay out of the mod's own code: which **mod loader** it runs on, and which
**Minecraft version** it was built for. Each has one home here.

## Loader: `Loader` and `ClientLoader`

`platform/Loader` has one copy per loader, same name and same signatures:

- `src/main/fabric/java/com/civillagers/platform/Loader.java`
- `src/main/neoforge/java/com/civillagers/platform/Loader.java`

The build compiles exactly one of them (`src/main/<loader>` is a source directory of `main` only on that
loader's nodes). `client/platform/ClientLoader` follows the same rule under `src/client/<loader>`.

Adding a loader call:

1. Add the method to **both** copies, with the same signature and Javadoc saying what it means, not how
   the loader does it.
2. Call it from common code. `checkLoaderSeam` fails if `src/main/java` or `src/client/java` imports
   `net.fabricmc`, `net.neoforged` or `net.minecraftforge`.

Entrypoints live beside the copies: `fabric/CivillagersFabric`, `neoforge/CivillagersNeoForge` and
their client counterparts. Each only calls `Civillagers.initialize()` / `CivillagersClient.initialize()`
and wires what is truly loader specific.

## Version: `Vanilla` and `ClientVanilla`

`platform/Vanilla` (and `client/platform/ClientVanilla`) is one class with Stonecutter blocks inside:

```java
public static Foo foo(Bar bar) {
    //? if >=26.1 {
    return bar.newName();
    //?} else {
    /*return bar.oldName();
    *///?}
}
```

- A `//?` block is allowed only under a `platform/` or `mixin/` package. `checkVersionSeam` fails
  anywhere else.
- A pure rename with no change of meaning (a class or method renamed) is a rule in
  `stonecutter.gradle.kts` instead, so no code has to know about it.
- When a difference reshapes a whole class rather than a call, the class itself lives under `platform/`
  with its `//?` blocks around the class body: `platform/SavingMob`, which a mob with saved values of
  its own extends because vanilla's save hooks changed shape in 1.21.6, and `client/platform/GuardModel`
  and `GuardRenderer`, whose type parameters and overridden methods changed with 1.21.2's render states.
  Keep what does not differ outside, in `client/render`.
- The file on disk is in the state of the **active** node (`26.3.x`). Edit it in that state and run
  `./gradlew "Reset active project"` before committing.
