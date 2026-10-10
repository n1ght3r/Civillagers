package com.civillagers.dev.benchmark;

import com.civillagers.village.Village;
import com.civillagers.village.Villages;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The patch of the overworld a run works in: force-loaded chunks, bells for the village scenarios, and the
 * guards and zombies the guard scenarios spawn. Every block the benchmark places goes through
 * {@link #place}, every entity through {@link #track}, every bell it gives the level's own villages
 * through {@link #addLevelAnchor}, and {@link #close} undoes all three and releases the chunks.
 *
 * <p>The benchmark is a dev tool and may force chunks; the mod itself never does.
 */
final class BenchmarkWorld {
    /** Chunks either side of the centre chunk kept loaded: an 11x11 square. */
    static final int CHUNK_RADIUS = 5;
    /** Villages sit one to a chunk on a square this wide, inside the loaded area. */
    static final int VILLAGE_GRID = 2 * CHUNK_RADIUS;
    /** Bells are buried this far below the ground, out of the guards' way. */
    private static final int BELL_DEPTH = 6;
    /** Candidate centres are this many chunks apart, tried in rings around (64, 64). */
    private static final int AREA_STEP = 32;
    private static final int AREA_RINGS = 3;
    /** Send to clients, no neighbour updates: a bell placed underground needs nothing else. */
    private static final int PLACE_FLAGS = 2;

    final MinecraftServer server;
    final ServerLevel level;
    private final List<long[]> pending = new ArrayList<>();
    private final List<long[]> forced = new ArrayList<>();
    private final Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
    private final List<Entity> entities = new ArrayList<>();
    private final List<BlockPos> levelAnchors = new ArrayList<>();
    private int chunkX;
    private int chunkZ;
    private boolean chosen;
    private String description = "not set up";

    BenchmarkWorld(MinecraftServer server) {
        this.server = server;
        this.level = server.overworld();
    }

    /** Chooses the area, then forces and loads its chunks a few at a time. True once every chunk is loaded. */
    boolean load(long deadlineNanos) {
        if (!chosen) {
            choose();
            chosen = true;
        }
        while (!pending.isEmpty()) {
            long[] chunk = pending.remove(pending.size() - 1);
            int x = (int) chunk[0];
            int z = (int) chunk[1];
            // Only chunks this run forced are released again; one the world already forces stays forced.
            if (level.setChunkForced(x, z, true)) forced.add(chunk);
            level.getChunk(x, z);
            if (System.nanoTime() >= deadlineNanos) return pending.isEmpty();
        }
        return true;
    }

    String progress() {
        int total = (2 * CHUNK_RADIUS + 1) * (2 * CHUNK_RADIUS + 1);
        return (total - pending.size()) + "/" + total + " chunks";
    }

    private void choose() {
        chunkX = 64;
        chunkZ = 64;
        search:
        for (int ring = 0; ring <= AREA_RINGS; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    int x = 64 + dx * AREA_STEP;
                    int z = 64 + dz * AREA_STEP;
                    // Asking the biome reads the generator, not the chunk, so no chunk loads here.
                    var biome = level.getBiome(new BlockPos((x << 4) + 8, 64, (z << 4) + 8));
                    if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_BEACH)) continue;
                    chunkX = x;
                    chunkZ = z;
                    break search;
                }
            }
        }
        for (int dx = -CHUNK_RADIUS; dx <= CHUNK_RADIUS; dx++) {
            for (int dz = -CHUNK_RADIUS; dz <= CHUNK_RADIUS; dz++) pending.add(new long[] {chunkX + dx, chunkZ + dz});
        }
        description = "chunks " + (chunkX - CHUNK_RADIUS) + ".." + (chunkX + CHUNK_RADIUS) + " x "
                + (chunkZ - CHUNK_RADIUS) + ".." + (chunkZ + CHUNK_RADIUS) + " around "
                + level.getBiome(center()).unwrapKey().map(key -> key.identifier().toString()).orElse("?");
    }

    String describe() {
        return description;
    }

    /** The ground at the middle of the area, where the guards stand. */
    BlockPos center() {
        return surface((chunkX << 4) + 8, (chunkZ << 4) + 8);
    }

    /** The first block above the ground at {@code x, z}. The chunk must be loaded. */
    BlockPos surface(int x, int z) {
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    /** Where the {@code index}th village's bell goes: the middle of its chunk, buried under the ground. */
    BlockPos villageBell(int index) {
        int x = ((chunkX - CHUNK_RADIUS + index % VILLAGE_GRID) << 4) + 8;
        int z = ((chunkZ - CHUNK_RADIUS + index / VILLAGE_GRID) << 4) + 8;
        return surface(x, z).below(BELL_DEPTH);
    }

    /** Places a block, remembering what was there the first time, so {@link #close} can put it back. */
    void place(BlockPos pos, BlockState state) {
        BlockPos key = pos.immutable();
        originals.putIfAbsent(key, level.getBlockState(key));
        level.setBlock(key, state, PLACE_FLAGS);
    }

    /** Remembers an entity the benchmark spawned, so {@link #close} can remove it. */
    <T extends Entity> T track(T entity) {
        if (entity != null) entities.add(entity);
        return entity;
    }

    /** Discards every tracked entity. */
    void discardEntities() {
        for (Entity entity : entities) {
            if (!entity.isRemoved()) entity.discard();
        }
        entities.clear();
    }

    /** Adds a bell to the level's own villages, as a real village would have, and remembers it. */
    Village addLevelAnchor(BlockPos pos) {
        levelAnchors.add(pos.immutable());
        return Villages.get(level).addAnchor(pos, level.getGameTime());
    }

    /** Undoes everything: entities, the level's bells, the blocks, the forced chunks. */
    void close() {
        discardEntities();
        Villages villages = Villages.get(level);
        for (BlockPos anchor : levelAnchors) {
            Village village = villages.byAnchor(anchor.asLong());
            if (village != null) villages.removeAnchor(village, anchor.asLong(), level.getGameTime());
        }
        levelAnchors.clear();
        List<Map.Entry<BlockPos, BlockState>> placed = new ArrayList<>(originals.entrySet());
        for (int i = placed.size() - 1; i >= 0; i--) {
            level.setBlock(placed.get(i).getKey(), placed.get(i).getValue(), PLACE_FLAGS);
        }
        originals.clear();
        for (long[] chunk : forced) level.setChunkForced((int) chunk[0], (int) chunk[1], false);
        forced.clear();
    }
}
