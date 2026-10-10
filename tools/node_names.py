"""What a node's name says: its Minecraft version and its mod loader.

A node is `<version>` on Fabric and `<version>-<loader>` on any other loader, as settings.gradle.kts
names it. The Gradle side asks `loaderOf` and `minecraftOf` in build-logic; every Python script asks
this module, so a new loader is one line in each.

    python tools/node_names.py matrix    # the CI matrix, one {"version", "loader"} per node, as JSON
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PROPERTIES = ROOT / "stonecutter.properties.toml"

FABRIC = "fabric"
LOADERS = (FABRIC, "neoforge")

_LOADER_TABLE = re.compile(r'^\s*\[(' + "|".join(LOADERS) + r')\."([^"]+)"\]\s*$')


def suffix(loader: str) -> str:
    """What follows the Minecraft version in the name of a node of `loader`: nothing on Fabric."""
    return "" if loader == FABRIC else f"-{loader}"


def loader_of(node: str) -> str:
    """The mod loader a node builds for: `neoforge` for `1.21.1-neoforge`, `fabric` for `26.3.x`."""
    for loader in LOADERS:
        if loader != FABRIC and node.endswith(suffix(loader)):
            return loader
    return FABRIC


def minecraft_of(node: str) -> str:
    """The node's name without its loader: `1.21.1` for `1.21.1-neoforge`. It names the shared table."""
    return node.removesuffix(suffix(loader_of(node)))


def nodes(path: Path = PROPERTIES) -> list[dict[str, str]]:
    """Every node with a loader table in stonecutter.properties.toml, in file order.

    `[fabric."26.3.x"]` is the node `26.3.x` and `[neoforge."1.21.1"]` the node `1.21.1-neoforge`; a
    shared `["1.21.1"]` table is not a node. The build reads a node's dependency versions from its loader
    table, so every node has one.
    """
    found = []
    for line in path.read_text(encoding="utf-8").splitlines():
        match = _LOADER_TABLE.match(line)
        if match:
            loader, minecraft = match.groups()
            found.append({"version": minecraft + suffix(loader), "loader": loader})
    return found


if __name__ == "__main__":
    if sys.argv[1:] != ["matrix"]:
        sys.exit(__doc__)
    print(json.dumps(nodes()))
