# Contributing to Civillagers

Thanks for helping out. Bug reports, translations, ideas and code are all welcome.

## Reporting a bug

Open an [issue](https://github.com/n1ght3r/Civillagers/issues/new/choose) with the bug report form.
The most useful details are:

- the full jar name, for example `Civillagers-0.1.0+26.3.jar`, which gives both the mod and the
  Minecraft version;
- Fabric or NeoForge, and the other mods installed;
- the steps that cause the problem, and the log or crash report if there is one.

Check the [changelog](CHANGELOG.md) first, in case the behaviour is an intended change.

## Suggesting a feature

Use the feature request form on the same page. For anything large, open an issue before starting
work, so we can agree on the approach first.

## Translations

Language files are in `src/main/resources/assets/civillagers/lang/`. To fix a translation, edit that
language's file. To add a language, copy `en_us.json`, rename it to the game's locale code (for example
`de_de.json`), and translate the values only. Keep the keys and any `%s` placeholders as they are.
`checkLang` fails the build when a file is missing a key `en_us.json` has.

## Code

1. Fork the repository and create a branch from `main`.
2. Read [AGENTS.md](AGENTS.md) and the `AGENTS.md` in the folder you are working in. They list the
   rules the build checks and where each part of the mod lives.
3. Run what CI runs, on at least one Fabric and the NeoForge node:

   ```bash
   ./gradlew ":26.3.x:ci" ":1.21.1-neoforge:ci"
   ```

4. Open a pull request that explains what changed and why. CI builds and tests every node.

A few rules that apply to most changes:

- The mod must build on every supported Minecraft version and on both loaders.
- Other mods stay optional. Never add a required dependency.
- Anything a player can see needs a line in every lang file.
- Player-facing changes get a line under `Unreleased` in [CHANGELOG.md](CHANGELOG.md).

## License

By contributing, you agree that your work is released under the
[GNU General Public License v3.0](LICENSE).
