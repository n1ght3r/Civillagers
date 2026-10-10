# Civillagers

[![Build](https://github.com/n1ght3r/Civillagers/actions/workflows/build.yml/badge.svg)](https://github.com/n1ght3r/Civillagers/actions/workflows/build.yml)
[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue.svg)](LICENSE)

Villages that grow into civilizations. Civillagers gives villages guards who patrol and defend them, a
village chief who decides what the village needs next, and builders who expand the village on their own.

**Status:** early development. Nothing is playable yet.

## Supported versions

| Minecraft | Fabric | NeoForge |
|---|---|---|
| 26.3 | ✅ | — |
| 26.2 | ✅ | — |
| 1.21.1 (and 1.21 on Fabric) | ✅ | ✅ |

## Building

Requires JDK 21 and 25; Gradle provisions them if they are missing.

```bash
./gradlew buildAndCollect
```

The jars land in `build/libs/`. See [AGENTS.md](AGENTS.md) for the full list of tasks and how the
project is laid out.

## Contributing

Bug reports, translations and pull requests are welcome; see [CONTRIBUTING.md](CONTRIBUTING.md).

## License

[GPL-3.0-only](LICENSE).
