## What and why

<!-- What this changes, and the problem it solves. Link the issue if there is one: "Closes #12". -->

## Checklist

- [ ] `./gradlew ":<node>:ci"` passes on the nodes this touches (CI runs every node)
- [ ] Player-facing text is in every lang file
- [ ] Player-facing changes have a line under `Unreleased` in `CHANGELOG.md`
- [ ] `./gradlew "Reset active project"` was run before committing, if the active node was switched
