"""Make a fresh throwaway world for an agent script, where the player arrives alive, on the ground, at
noon, with nothing hostile around.

    python tools/agent/new_world.py <node> <World> [--terrain] [--biome <id>] [--from <source world>] [--datapack <folder>]... [--force]

then run the script with `-Pquickplay=<World>`. It builds run/<node>/saves/<World> out of the world
settings of another world of the same node (`--from`, or the first one in run/<node>/saves), since a
level.dat cannot be written for every Minecraft version by hand.

**By default the world is a sky floor**: a superflat overworld of nothing but one layer of stone at
y 199 (`FLOOR_Y`), one biome (`--biome`, plains by default), no structures, features or lakes. The
player stands on it at y 200, at the source world's spawn x and z. Most scripts build their scene at
y 199 to 206 anyway, so they land on the floor; nothing generates under it for the player to fall into,
no snow, cave or mob is near, and chunks generate in a fraction of the time and disk of real terrain.
`fillbiome` still works on it, and water above y 100 grades as it did over real terrain.

**`--terrain`** keeps the source world's own generator instead, with its seed, for what needs real
ground: docs screenshots (never taken on the sky floor), natural biomes, heights and caves. The player
arrives on the source world's spawn point, which may be in powder snow or water: a terrain script
teleports first.

What it drops or changes either way, and why:

- **The player.** A 1.21.x level.dat carries the singleplayer player (position, motion, air, effects)
  in `Data.Player`, so a copy dropped them wherever the other world left them, often mid air or under
  water, over terrain generated anew. Without it the player is placed at world spawn, as in a new world.
  From 26.1 the player lives in `players/`, which is never copied.
- **Time and weather** are frozen at noon and clear, so a capture looks the same every run.
- **Mobs, patrols, traders and phantoms** do not spawn, so nothing attacks the player mid script.
- **The spawn radius** is 0, so the player stands on the spawn point itself.

`--datapack` copies a data pack folder into the world's datapacks, for a script that needs one.

Difficulty is left alone: in Peaceful the hostile mobs a guard script spawns would vanish at once.

The world is recreated from nothing on every run. One that exists is deleted only when this tool made
it (it leaves a `.agent-world` file); `--force` deletes any world of that name, for the first run over
a world made by hand.
"""

import argparse
import shutil
import sys
from pathlib import Path

import nbt

ROOT = Path(__file__).resolve().parents[2]
MARKER = ".agent-world"

# 1.21.x: string rules in level.dat's Data.GameRules.
LEGACY_RULES = {
    "doDaylightCycle": "false",
    "doWeatherCycle": "false",
    "doMobSpawning": "false",
    "doPatrolSpawning": "false",
    "doTraderSpawning": "false",
    "doInsomnia": "false",
    "spawnRadius": "0",
}

# 26.1+: typed rules in data/minecraft/game_rules.dat, true and false as bytes.
RULES = {
    "minecraft:advance_time": nbt.byte(0),
    "minecraft:advance_weather": nbt.byte(0),
    "minecraft:spawn_mobs": nbt.byte(0),
    "minecraft:spawn_monsters": nbt.byte(0),
    "minecraft:spawn_patrols": nbt.byte(0),
    "minecraft:spawn_wandering_traders": nbt.byte(0),
    "minecraft:spawn_phantoms": nbt.byte(0),
    "minecraft:respawn_radius": nbt.integer(0),
}

# 26.1+: the world settings that moved out of level.dat. Everything else under data/ is the old
# world's own state (scoreboard, boss bars, raids) and stays behind.
MOVED_OUT = ("world_gen_settings.dat", "game_rules.dat", "weather.dat", "world_clocks.dat")

# The sky floor: one layer at this height, the player on top of it. The overworld starts at y -64 on
# every node.
FLOOR_Y = 199
MIN_Y = -64
FLOOR_BLOCK = "minecraft:stone"

NOON = 6000
CLEAR_FOR = 1_000_000


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("node", help="the Gradle node, e.g. 1.21.1 or 26.3.x-neoforge")
    parser.add_argument("world", help="the world to make, as -Pquickplay names it")
    parser.add_argument("--terrain", action="store_true", help="real terrain from the source world's generator, instead of the sky floor")
    parser.add_argument("--biome", default="minecraft:plains", help="the sky floor's one biome (default: minecraft:plains)")
    parser.add_argument("--from", dest="source", help="the world whose settings to reuse (default: the first one there)")
    parser.add_argument("--datapack", action="append", default=[], help="a data pack folder to copy into the world")
    parser.add_argument("--force", action="store_true", help="delete a world of that name this tool did not make")
    args = parser.parse_args()

    saves = ROOT / "run" / args.node / "saves"
    target = saves / args.world
    source = pick_source(saves, args.world, args.source)

    if target.exists():
        if not (target / MARKER).exists() and not args.force:
            sys.exit(f"{target} exists and was not made by this tool; pass --force to replace it")
        shutil.rmtree(target)
    target.mkdir(parents=True)

    level, name = nbt.load(source / "level.dat")
    data = level["Data"]
    data["LevelName"] = nbt.string(args.world)
    if "GameRules" in data:
        fresh_legacy(data)
    if not args.terrain:
        if "WorldGenSettings" in data:
            sky_floor(data["WorldGenSettings"], args.biome)
        stand_on_floor(data)
    nbt.save(target / "level.dat", level, name)

    moved = source / "data" / "minecraft"
    if (moved / "world_gen_settings.dat").exists():
        (target / "data" / "minecraft").mkdir(parents=True)
        for file in MOVED_OUT:
            if (moved / file).exists():
                shutil.copyfile(moved / file, target / "data" / "minecraft" / file)
        fresh_moved_out(target / "data" / "minecraft")
        if not args.terrain:
            settings_file = target / "data" / "minecraft" / "world_gen_settings.dat"
            root, root_name = nbt.load(settings_file)
            sky_floor(root["data"], args.biome)
            nbt.save(settings_file, root, root_name)

    for pack in args.datapack:
        pack = (ROOT / pack).resolve() if not Path(pack).is_absolute() else Path(pack)
        if not pack.is_dir():
            sys.exit(f"{pack} is not a folder")
        shutil.copytree(pack, target / "datapacks" / pack.name)

    (target / MARKER).write_text(f"Made by tools/agent/new_world.py from {source.name}.\n", encoding="utf-8")
    print(f"{target.relative_to(ROOT)} made from {source.name}")


def pick_source(saves, world, named):
    if named:
        source = saves / named
        if not (source / "level.dat").exists():
            sys.exit(f"{source} has no level.dat")
        return source
    candidates = sorted(p for p in saves.glob("*/level.dat") if p.parent.name != world and not (p.parent / MARKER).exists())
    if not candidates:
        candidates = sorted(p for p in saves.glob("*/level.dat") if p.parent.name != world)
    if not candidates:
        sys.exit(f"No world in {saves} to take settings from: start ./gradlew \":<node>:runClient\" once and create one")
    return candidates[0].parent


def fresh_legacy(data):
    """1.21.x: everything in level.dat."""
    data.pop("Player", None)
    for key, value in (("DayTime", NOON), ("raining", 0), ("thundering", 0), ("rainTime", CLEAR_FOR),
                       ("thunderTime", CLEAR_FOR), ("clearWeatherTime", CLEAR_FOR)):
        keep_type(data, key, value)
    rules = data["GameRules"]
    for rule, value in LEGACY_RULES.items():
        rules[rule] = nbt.string(value)


def fresh_moved_out(folder):
    """26.1+: the rules and the weather in their own files. The clock stays the old world's, stopped;
    a script that needs a time of day sets it with a command."""
    rules_file = folder / "game_rules.dat"
    if rules_file.exists():
        root, name = nbt.load(rules_file)
        root["data"].update(RULES)
        nbt.save(rules_file, root, name)
    weather_file = folder / "weather.dat"
    if weather_file.exists():
        root, name = nbt.load(weather_file)
        weather = root["data"]
        for key, value in (("raining", 0), ("thundering", 0), ("rain_time", CLEAR_FOR),
                           ("thunder_time", CLEAR_FOR), ("clear_weather_time", CLEAR_FOR)):
            keep_type(weather, key, value)
        nbt.save(weather_file, root, name)


def sky_floor(settings, biome):
    """Swaps the overworld's generator for a superflat one: air up to FLOOR_Y, one layer of FLOOR_BLOCK.
    The same shape from 1.20.1 to 26.x, in level.dat's WorldGenSettings or in world_gen_settings.dat."""
    layers = nbt.TagList(nbt.COMPOUND, [
        {"block": nbt.string("minecraft:air"), "height": nbt.integer(FLOOR_Y - MIN_Y)},
        {"block": nbt.string(FLOOR_BLOCK), "height": nbt.integer(1)},
    ])
    flat = {
        "layers": layers,
        "biome": nbt.string(biome),
        "features": nbt.byte(0),
        "lakes": nbt.byte(0),
        "structure_overrides": nbt.TagList(nbt.END, []),
    }
    overworld = settings["dimensions"]["minecraft:overworld"]
    overworld["generator"] = {"type": nbt.string("minecraft:flat"), "settings": flat}
    for key in ("generate_features", "generate_structures"):
        if key in settings:
            settings[key] = nbt.byte(0)


def stand_on_floor(data):
    """Moves the spawn point onto the floor, keeping its x and z. 1.21.x and older: SpawnY. 26.1+:
    spawn.pos, an int array."""
    if "SpawnY" in data:
        data["SpawnY"] = nbt.integer(FLOOR_Y + 1)
    spawn = data.get("spawn")
    if isinstance(spawn, dict) and "pos" in spawn:
        x, _, z = spawn["pos"].value
        spawn["pos"] = nbt.Tag(spawn["pos"].type, [x, FLOOR_Y + 1, z])


def keep_type(compound, key, value):
    """Sets a number the file already has, in the type it has it, so a version that widened one reads it."""
    if key in compound:
        compound[key] = nbt.Tag(compound[key].type, value)


if __name__ == "__main__":
    main()
