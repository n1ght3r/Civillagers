# tools/agent — agent client scripts

Request files for the agent queue: one JSON request per line, `//` lines are comments, and `expect` /
`checks` beside a request are what `drive.py --verify` asserts afterwards. The protocol, the queue and
every command are in [src/dev/java/com/civillagers/dev/agent/AGENTS.md](../../src/dev/java/com/civillagers/dev/agent/AGENTS.md).
Every path is relative to the repository root; each script's header says how to run it.

| File | What |
|---|---|
| `drive.py` | Sends a file or standard input to a queue, waits for the answers, and `--verify`s a finished run |
| `new_world.py`, `nbt.py` | Makes a throwaway world for a script: a sky floor at y 199 by default, noon, clear, no mob spawning; `--terrain` for real ground |
| `clean.py` | Deletes the throwaway worlds `new_world.py` made |
| `smoke/server-probe.jsonl` | A dedicated server's queue answers with nobody online |
| `smoke/boot.jsonl` | A driven client comes up, opens its world and never takes the mouse |
| `shots/guards.jsonl` | Five guards in different gear, captured to `run/<node>/agent/client/screenshots/guards.png` |

`new_world.py` takes its settings from another world of the node (`--from <world>`, or the first one in
`run/<node>/saves`), so a node needs one world there first: open one with `runClient`, or copy the
benchmark's world (`run/<node>/civillagers-benchmark`) into `saves/`.
