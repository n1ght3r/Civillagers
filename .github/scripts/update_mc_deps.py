"""Bumps the Minecraft-bound dependency versions in stonecutter.properties.toml.

Dependabot cannot do this. The versions live in a Stonecutter properties file it does not read, and even
if it did, it would offer every node the newest Fabric API, the one built for the newest Minecraft. This
asks Modrinth instead, once per node, for the newest upload of each mod that is built for that node's
loader and lists the Minecraft version that node compiles against, and writes it back in place, comments
and layout untouched. Adapted from ThirstWasTaken2's script of the same name.

    python .github/scripts/update_mc_deps.py            # rewrite the file
    python .github/scripts/update_mc_deps.py --dry-run  # only report
    python .github/scripts/update_mc_deps.py --summary build/deps-summary.md

Rules:
- A node compiles against the Minecraft version settings.gradle.kts gives it (`26.3.x` -> `26.3`), so that
  is the version a candidate has to list. A newer Minecraft patch is a manual bump.
- A node takes the uploads of the loader its name ends in; a node with no loader in its name takes Fabric
  ones (`tools/node_names.py`). Values are read and rewritten in the node's loader table, or else in the
  shared table of its Minecraft version.
- Only release uploads are taken, unless the pinned version is itself a beta or alpha.
- A candidate has to be published after the pinned version. Nothing is ever downgraded.
- Fabric Loader is one global value from Fabric's meta API, stable builds only. NeoForge comes from
  maven.neoforged.net: the newest build for the same Minecraft version, releases only unless the pinned
  build is a beta. Both raise the minimum players need, since the manifests write them as lower bounds.
- Loom and the Gradle plugins are left alone: Dependabot covers the plugins, and a Loom bump tends to need
  a Gradle bump alongside it.

Standard library only, so CI needs nothing but a Python interpreter.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

sys.path.insert(0, str(ROOT / "tools"))
from node_names import LOADERS, loader_of, minecraft_of  # noqa: E402

PROPERTIES = ROOT / "stonecutter.properties.toml"
SETTINGS = ROOT / "settings.gradle.kts"

MODRINTH = "https://api.modrinth.com/v2"
FABRIC_META = "https://meta.fabricmc.net/v2/versions/loader"
NEOFORGE_METADATA = "https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml"
# The same list from the repository's own API, asked when the metadata file keeps failing.
NEOFORGE_API = "https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/neoforge"
ATTEMPTS = 3
# Modrinth asks every client for a User-Agent that identifies the project.
USER_AGENT = "n1ght3r/Civillagers dependency updater (github.com/n1ght3r/Civillagers)"


@dataclass(frozen=True)
class ModrinthDep:
    key: str
    """The key after `deps.` in the properties file."""
    project: str
    """Modrinth project slug."""
    by_id: bool = False
    """Pinned by Modrinth version id instead of version number. Use it when a project's version numbers are
    shared between loaders or Minecraft versions, and put the number in a comment above the id."""


# Every per-node dependency the build resolves from Modrinth, or from a Maven that publishes the same
# version numbers (Fabric API). Add a line here when a script gains a `deps.*` property.
MODRINTH_DEPS = [
    ModrinthDep("fabric_api", "fabric-api"),
    ModrinthDep("modmenu", "modmenu"),
]


@dataclass
class Change:
    node: str | None
    """Node, or None for a global value."""
    key: str
    old: str
    new: str
    old_label: str
    """What a person reads as the old version; differs from `old` for id-pinned dependencies."""
    new_label: str
    url: str


class Unreachable(Exception):
    """A source that kept failing. The run skips what needed it instead of failing whole."""


def get_json(url: str):
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.load(response)


def node_minecraft_versions() -> dict[str, str]:
    """Maps each node to the Minecraft version it compiles against, from settings.gradle.kts."""
    text = SETTINGS.read_text(encoding="utf-8")
    # Commented-out lines, such as the NeoForge 26.x example, are not nodes.
    text = "\n".join(line for line in text.splitlines() if not line.lstrip().startswith("//"))
    nodes = dict(re.findall(r'\bversion\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*\)', text))
    if not nodes:
        sys.exit(f"No Stonecutter versions found in {SETTINGS.name}.")
    return nodes


class Properties:
    """The properties file as lines, so a rewrite keeps every comment and blank line.

    Layered by loader: `["1.21.1"]` holds what both loaders of that Minecraft version share,
    `[fabric."1.21.1"]` and `[neoforge."1.21.1"]` what only one of them has.
    """

    def __init__(self, path: Path):
        self.path = path
        # newline="" keeps CRLF on a Windows checkout, so the rewrite only touches the changed values.
        with path.open(encoding="utf-8", newline="") as file:
            self.lines = file.read().splitlines(keepends=True)

    @staticmethod
    def _pattern(key: str) -> re.Pattern[str]:
        return re.compile(r'^(\s*' + re.escape(key) + r'\s*=\s*")([^"]*)(".*)$', re.DOTALL)

    def _find_in(self, section: tuple[str | None, str] | None, key: str) -> tuple[int, str] | None:
        current: tuple[str | None, str] | None = None
        pattern = self._pattern(key)
        for index, line in enumerate(self.lines):
            header = re.match(r'^\s*\[(?:(' + "|".join(LOADERS) + r')\.)?"([^"]+)"\]\s*$', line)
            if header:
                current = (header.group(1), header.group(2))
                continue
            if current == section:
                match = pattern.match(line)
                if match:
                    return index, match.group(2)
        return None

    def find(self, node: str | None, key: str) -> tuple[int, str] | None:
        """Line index and value of `key` for `node`: its loader table, else its version's shared table, or
        the top level when node is None."""
        if node is None:
            return self._find_in(None, key)
        version = minecraft_of(node)
        return self._find_in((loader_of(node), version), key) or self._find_in((None, version), key)

    def has_node(self, node: str) -> bool:
        header = f'[{loader_of(node)}."{minecraft_of(node)}"]'
        return any(line.strip() == header for line in self.lines)

    def set(self, index: int, key: str, value: str) -> None:
        match = self._pattern(key).match(self.lines[index])
        assert match, self.lines[index]
        self.lines[index] = match.group(1) + value + match.group(3)

    def replace_in_comment_above(self, index: int, old: str, new: str) -> None:
        """Keeps a comment such as `# 2.5.2+mc1.21.1, the Fabric upload.` in step with the id below it."""
        above = index - 1
        if above >= 0 and self.lines[above].lstrip().startswith("#") and old in self.lines[above]:
            self.lines[above] = self.lines[above].replace(old, new)

    def save(self) -> None:
        self.path.write_text("".join(self.lines), encoding="utf-8", newline="")


def numeric(version: str) -> tuple[int, ...]:
    return tuple(int(part) for part in re.findall(r"\d+", version))


def modrinth_version(project: str, id_or_number: str) -> dict | None:
    try:
        return get_json(f"{MODRINTH}/project/{project}/version/{urllib.parse.quote(id_or_number, safe='')}")
    except urllib.error.HTTPError as error:
        if error.code == 404:
            return None
        raise


def modrinth_candidates(project: str, minecraft: str, loader: str) -> list[dict]:
    query = urllib.parse.urlencode({
        "loaders": json.dumps([loader]),
        "game_versions": json.dumps([minecraft]),
        "include_changelog": "false",
    })
    versions = get_json(f"{MODRINTH}/project/{project}/version?{query}")
    return sorted(versions, key=lambda v: v["date_published"], reverse=True)


def check_modrinth(props: Properties, node: str, minecraft: str, dep: ModrinthDep, changes: list[Change],
                   warnings: list[str]) -> None:
    found = props.find(node, f"deps.{dep.key}")
    if found is None:
        return
    index, pinned = found

    current = modrinth_version(dep.project, pinned)
    if current is None:
        warnings.append(f"`{node}` `deps.{dep.key}` = `{pinned}` was not found on Modrinth ({dep.project}); skipped.")
        return

    channels = {"release", current["version_type"]}
    candidates = modrinth_candidates(dep.project, minecraft, loader_of(node))
    newest = next((v for v in candidates if v["version_type"] in channels), None)
    if newest is None or newest["date_published"] <= current["date_published"]:
        return
    value = newest["id"] if dep.by_id else newest["version_number"]
    if value == pinned:
        return

    props.set(index, f"deps.{dep.key}", value)
    if dep.by_id:
        props.replace_in_comment_above(index, current["version_number"], newest["version_number"])
    changes.append(Change(node, dep.key, pinned, value, current["version_number"], newest["version_number"],
                          f"https://modrinth.com/mod/{dep.project}/version/{newest['id']}"))


def check_fabric_loader(props: Properties, changes: list[Change]) -> None:
    found = props.find(None, "deps.fabric_loader")
    if found is None:
        return
    index, pinned = found
    stable = [v["version"] for v in get_json(FABRIC_META) if v.get("stable")]
    if not stable:
        return
    newest = max(stable, key=numeric)
    if numeric(newest) <= numeric(pinned):
        return
    props.set(index, "deps.fabric_loader", newest)
    changes.append(Change(None, "fabric_loader", pinned, newest, pinned, newest,
                          "https://github.com/FabricMC/fabric-loader/releases"))


_neoforge_versions: list[str] | Unreachable | None = None


def neoforge_versions() -> list[str]:
    """Fetched once per run, and a failure remembered too, so a second node does not wait through the
    retries again. maven.neoforged.net now and then answers 404 or times out on a file that is there, so a
    failure is retried, then the API asked, before giving up."""
    global _neoforge_versions
    if _neoforge_versions is None:
        sources = [
            (NEOFORGE_METADATA, lambda body: re.findall(r"<version>([^<]+)</version>", body)),
            (NEOFORGE_API, lambda body: json.loads(body)["versions"]),
        ]
        errors = []
        for url, parse in sources:
            request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            error: object = None
            for attempt in range(ATTEMPTS):
                try:
                    with urllib.request.urlopen(request, timeout=30) as response:
                        versions = parse(response.read().decode("utf-8"))
                    if versions:
                        _neoforge_versions = versions
                        return versions
                    error = "no versions listed"
                except (urllib.error.URLError, TimeoutError, ValueError, KeyError) as caught:
                    error = caught
                print(f"{url}: {error}, attempt {attempt + 1} of {ATTEMPTS}", file=sys.stderr)
                if attempt < ATTEMPTS - 1:
                    time.sleep(10 * (attempt + 1))
            errors.append(f"{url}: {error}")
        _neoforge_versions = Unreachable("; ".join(errors))
    if isinstance(_neoforge_versions, Unreachable):
        raise _neoforge_versions
    return _neoforge_versions


def check_neoforge(props: Properties, node: str, changes: list[Change]) -> None:
    """NeoForge's version starts with the Minecraft version it is built for and ends with the build number,
    `21.1.256` for 1.21.1 and `26.3.0.57-beta` for 26.3, so only builds sharing everything but the pinned
    version's last part are candidates."""
    found = props.find(node, "deps.neoforge")
    if found is None:
        return
    index, pinned = found
    prefix = ".".join(pinned.split("-")[0].split(".")[:-1]) + "."
    allow_beta = "-" in pinned
    candidates = [v for v in neoforge_versions() if v.startswith(prefix) and (allow_beta or "-" not in v)]
    if not candidates:
        return
    newest = max(candidates, key=numeric)
    if numeric(newest) <= numeric(pinned):
        return
    props.set(index, "deps.neoforge", newest)
    changes.append(Change(node, "neoforge", pinned, newest, pinned, newest,
                          "https://projects.neoforged.net/neoforged/neoforge"))


def summary(changes: list[Change], warnings: list[str]) -> str:
    out = ["Updates the Minecraft-bound dependencies in `stonecutter.properties.toml`. Each candidate is an "
           "upload for its node's loader that lists the Minecraft version the node compiles against.", ""]
    if changes:
        out += ["| Node | Dependency | From | To |", "| --- | --- | --- | --- |"]
        out += [f"| {c.node or 'all'} | `{c.key}` | `{c.old_label}` | [`{c.new_label}`]({c.url}) |" for c in changes]
        out.append("")
    notes = []
    if any(c.key == "fabric_loader" for c in changes):
        notes.append("Fabric Loader is written into `fabric.mod.json` as `>=`, so this raises the minimum "
                     "loader players need.")
    if any(c.key == "neoforge" for c in changes):
        notes.append("NeoForge is written into `neoforge.mods.toml` as the lower bound, so this raises the "
                     "minimum NeoForge players need.")
    notes += warnings
    if notes:
        out += ["### Notes", ""] + [f"- {note}" for note in notes] + [""]
    return "\n".join(out)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--dry-run", action="store_true", help="report what would change without writing")
    parser.add_argument("--summary", type=Path, help="write a Markdown summary here, for a pull request body")
    args = parser.parse_args()

    props = Properties(PROPERTIES)
    changes: list[Change] = []
    warnings: list[str] = []

    check_fabric_loader(props, changes)
    for node, minecraft in node_minecraft_versions().items():
        if not props.has_node(node):
            warnings.append(f"`{node}` is in settings.gradle.kts but has no loader table in {PROPERTIES.name}; skipped.")
            continue
        for dep in MODRINTH_DEPS:
            check_modrinth(props, node, minecraft, dep, changes, warnings)
        if loader_of(node) == "neoforge":
            try:
                check_neoforge(props, node, changes)
            except Unreachable as error:
                # A NeoForge outage should not hold back the Modrinth bumps found above.
                warnings.append(f"`{node}` `deps.neoforge` was not checked, maven.neoforged.net did not answer ({error}).")

    for change in changes:
        print(f"{change.node or 'all'}: {change.key} {change.old_label} -> {change.new_label}")
    for warning in warnings:
        print(f"warning: {warning}", file=sys.stderr)
        if os.environ.get("GITHUB_ACTIONS") == "true":
            print(f"::warning::{warning}")
    if not changes:
        print("Everything is up to date.")
    elif not args.dry_run:
        props.save()

    if args.summary:
        args.summary.parent.mkdir(parents=True, exist_ok=True)
        args.summary.write_text(summary(changes, warnings), encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main())
