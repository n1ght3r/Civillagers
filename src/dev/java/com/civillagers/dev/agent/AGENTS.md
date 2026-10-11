# The agent client

A way for an agent to drive a real Minecraft client or server and read real values out of it: where the
guards are and what they wear, the villages, a command's feedback, a capture of the frame.
[src/dev/java/AGENTS.md](../../../../AGENTS.md) is the source set it belongs to.

`core/` is the queue, the reply envelope, the dispatch loop and the watchdog, in plain Java and Gson;
`checkAgentCore` fails the build if it imports Minecraft, a loader or the mod. `game/` is what any process
answers (`CivAgent`, `CommonProbes`, `ServerProbes`); `client/` is what only a client answers, and is
loaded only from the client entrypoints, so a dedicated server never touches a client class.

## Driving a client while you work

A driven client never takes the mouse pointer: `MouseGrabMixin` refuses Minecraft's grab outright, so the
cursor is never hidden or trapped in the window, even when the window is clicked or comes to the front.
Nothing the agent does goes through the operating system's mouse or keyboard: commands go through the
player's connection, the view through the player's rotation, captures through the framebuffer, so a
driven client answers the same whether its window is in front, behind another window or never focused.
It also stops pausing when it loses focus, keeps the size the run task gives it (it is never maximised
or made full screen), and shows what the script is doing in its window title.

`-Pagent=<file>` implies it; `-Pdriven` turns it on for a client you send requests to by hand. It is off
otherwise, because a person cannot play a client that never takes the mouse.

The one thing it does not stop: the window still opens, and Windows may bring a new window to the front
once when it appears. Typing goes to whichever window is in front, so glance at it when a client starts.

With `-Pagent`, `-Pdriven` or `-Pquickplay`, `runClient` first marks vanilla's first-launch accessibility
screen as seen and turns the focus pause off in `run/<node>/options.txt` (`agentRunDirectory`), since that
screen otherwise stands between `-Pquickplay` and the world with nobody to press Continue.

## The queue

Two text files in a directory, one line of JSON each way:

    run/<node>/agent/<name>/
        ready.json    written once the game will answer: startedAt, pid, the command list
        in.jsonl      the agent appends one request a line
        out.jsonl     the game appends one reply a line
        previous-*    the run before this one
        screenshots/  what client.capture wrote

`<name>` is `server` or `client`, or `-Dcivillagers.agent=<name>`. A request is
`{"id": "...", "command": "...", "args": {...}}`; the reply echoes `id`, says `ok`, and carries `result`
or `error`. Requests are answered in order, one at a time: a `wait` or a capture holds the next request
until it has answered, so a file of requests is a sequence.

Wait for this run's `ready.json` (its `startedAt`), not for the file: the previous run leaves one behind.
One game owns a queue directory, through a lock on `queue.lock`; a second game of the same node leaves it
alone and says who owns it.

## Running a script

```bash
python tools/agent/new_world.py 26.3.x GuardsAgent
./gradlew ":26.3.x:runClient" -Pagent=tools/agent/shots/guards.jsonl -Pquickplay=GuardsAgent
python tools/agent/drive.py run/26.3.x/agent/client tools/agent/shots/guards.jsonl --verify --max-age 10
```

`-Pagent=<file>` answers the file once the game is up and then stops it; the last log line is
`[CivAgent] DONE ...`. `-Pquickplay=<world>` opens that world of `run/<node>/saves` from launch.
`drive.py --verify` checks the `expect` and `checks` written beside each request against what the run
recorded, and exits 1 on a mismatch; `--max-age` refuses a stale `ready.json`. Without `-Pagent`, send
requests to a running game with `drive.py <queue> <file>` or by appending to `in.jsonl`.

An unattended run always ends: with no request answered for 300 s it stops (`-Dcivillagers.agent.stallSeconds`),
and a game thread that stops ticking for 300 s is halted (`-Dcivillagers.agent.frozenSeconds`).

## The commands

| Command | Arguments | Answers |
|---|---|---|
| `probe` | | side, loader, Minecraft version, run directory, queue, whether a server runs, the command list |
| `wait` | `ticks` | once that many game ticks have run |
| `stop` | | after stopping this process |
| `jvm.histogram` | `classes` (exact names), `prefixes`, `ignore` | live instances after a full collection, as `jcmd GC.class_histogram` counts them: each of `classes` in order (a list, since a dotted path cannot name a class), and every class under `prefixes` with instances, leaving out lambdas, enums and `ignore` |
| `server.info` | | dedicated, tick count, players, difficulty |
| `server.command` | `command`, `as` (a player name, or `@only` for the one player online) | the command's feedback, collected; it runs with the console's permission |
| `server.guards` | | every guard: position, health, sleeping, aggressive, target type, gear by slot |
| `server.villages` | | every village: id, level, center, radius, bells, members, guards, beds, workstations |
| `client.info` | | frame size, fps, screen, in a world, quickplay and whether it failed, HUD hidden, `driven`, `mouseGrabbed` |
| `client.state` | | alive, health, dimension, position, yaw, pitch |
| `client.look` | `yaw`, `pitch`, or `at: [x, y, z]` | the rotation now |
| `client.hud.toggle` | | whether the HUD is hidden now, as F1 hides it |
| `client.capture` | `name` | a PNG in `screenshots/`, once it is on disk |
| `client.command` | `command` | after sending it through the player's connection (needs the player's own permission) |
| `client.world.leave` | | once the client is back on the title screen and the integrated server has stopped |
| `client.world.open` | `world` (`-Pquickplay`'s by default) | once the player is in that world of `run/<node>/saves` |

## Rules

- A capture is evidence for a person, never an assertion; assert on values.
- No change to the mod for the agent's sake; read what is missing from this source set.
- `core/` stays free of Minecraft, loaders and the mod (`checkAgentCore`).
- A version difference goes in `client/AgentClientVanilla`, a loader difference in `DevLoader` or
  `DevClientLoader`.
