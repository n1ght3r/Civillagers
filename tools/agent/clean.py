"""Delete the throwaway worlds agent scripts and gametests leave behind in run/, and nothing else.

    python tools/agent/clean.py [<node>...] [--world <World>] [--screenshots] [--dry-run]

With no node it goes through every run/<node>/. What it deletes, because the next run makes it again:

- **Worlds `new_world.py` made**, the ones holding a `.agent-world` file. `new_world.py` recreates a
  world from nothing on every run, so one kept between runs is only disk. `--world` limits this to one
  world, for cleaning up straight after a script.
- **`gametest/world`**, which `runGametest` deletes and makes again on every run, so the one a run
  leaves behind is only disk.

With `--screenshots` it also empties `agent/<queue>/screenshots/`: captures and `client.record`
frames. Copy any keeper out first; the docs images live in docs/public/screenshots, not here.

What it never touches:

- **A world without `.agent-world`.** `New World` and the other hand-made worlds are the seed and
  spawn point `new_world.py` copies from, and scenes staged by hand for docs screenshots cannot be
  remade by a script. Those are listed as kept, with their size, so a person can decide.
- **A world a game still has open.** Windows refuses to delete it; it is reported and skipped.
- The queue files (`in.jsonl`, `out.jsonl`, `previous-*`), config, logs, mods and the benchmark world.
"""

import argparse
import os
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RUN = ROOT / "run"
MARKER = ".agent-world"


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("nodes", nargs="*", help="nodes to clean, e.g. 1.21.1 26.3.x-neoforge (default: all)")
    parser.add_argument("--world", help="only this world of run/<node>/saves, and only if new_world.py made it")
    parser.add_argument("--screenshots", action="store_true", help="also empty agent/<queue>/screenshots/")
    parser.add_argument("--dry-run", action="store_true", help="list what would go, delete nothing")
    args = parser.parse_args()

    nodes = [RUN / n for n in args.nodes] if args.nodes else sorted(p for p in RUN.iterdir() if p.is_dir())
    for node in nodes:
        if not node.is_dir():
            sys.exit(f"{node.relative_to(ROOT)} does not exist")

    freed = 0
    kept = []
    for node in nodes:
        saves = node / "saves"
        worlds = [saves / args.world] if args.world else sorted(p for p in saves.glob("*") if p.is_dir())
        for world in worlds:
            if not world.is_dir():
                continue
            if (world / MARKER).exists():
                freed += remove(world, args.dry_run)
            else:
                kept.append(world)
        if args.world:
            continue
        freed += remove(node / "gametest" / "world", args.dry_run)
        if args.screenshots:
            for shots in sorted(node.glob("agent/*/screenshots")):
                freed += remove(shots, args.dry_run)

    if kept:
        print("\nKept, not made by new_world.py:")
        for world in kept:
            print(f"  {size(du(world)):>8}  {world.relative_to(ROOT)}")
    print(f"\n{'Would free' if args.dry_run else 'Freed'} {size(freed)}")


def remove(path, dry_run):
    if not path.exists():
        return 0
    total = du(path)
    if not dry_run:
        try:
            shutil.rmtree(long_path(path))
        except OSError as error:
            print(f"  skipped   {path.relative_to(ROOT)}: {error.strerror or error} (is a game running on it?)")
            return 0
    print(f"  {size(total):>8}  {path.relative_to(ROOT)}")
    return total


def long_path(path):
    """Windows stops at 260 characters unless the path says otherwise; a gametest world's region and
    player files sit deep enough under a worktree to pass it."""
    if os.name == "nt":
        return "\\\\?\\" + str(path.resolve())
    return path


def du(path):
    total = 0
    for dirpath, _, files in os.walk(long_path(path)):
        for file in files:
            try:
                total += os.path.getsize(os.path.join(dirpath, file))
            except OSError:
                pass
    return total


def size(n):
    for unit in ("B", "KB", "MB", "GB"):
        if n < 1024 or unit == "GB":
            return f"{n:.0f} {unit}" if unit == "B" else f"{n:.1f} {unit}"
        n /= 1024


if __name__ == "__main__":
    main()
