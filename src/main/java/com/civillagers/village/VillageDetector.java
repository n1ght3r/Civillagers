package com.civillagers.village;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.ChunkPos;

import java.util.List;
import java.util.function.Predicate;

/**
 * Finds villages from vanilla's points of interest: a bell (the meeting point) with a bed (a home) within
 * {@link Village#LINK_DISTANCE} of it founds a village, or joins the one it links to.
 *
 * <p>Every query here reads only chunks that are loaded. {@link PoiManager} would read an unloaded chunk's
 * POIs from disk, on the main thread, so the chunks are walked one by one and checked first. Queries are
 * rate-limited by their callers, never run every tick.
 */
public final class VillageDetector {
    /** Ticks between two discovery scans in one level; each scan looks around one player. */
    public static final int DISCOVERY_SPACING = 20;
    /** How far around a player discovery looks, in chunks: a 7x7 square. */
    public static final int DISCOVERY_CHUNK_RADIUS = 3;

    static final Predicate<Holder<PoiType>> BELL = holder -> holder.is(PoiTypes.MEETING);
    static final Predicate<Holder<PoiType>> BED = holder -> holder.is(PoiTypes.HOME);
    static final Predicate<Holder<PoiType>> WORKSTATION = holder -> holder.is(PoiTypeTags.ACQUIRABLE_JOB_SITE);

    private VillageDetector() { }

    /** Every {@link #DISCOVERY_SPACING} ticks, looks for new villages around the next player in the level. */
    static void discoverNearPlayers(ServerLevel level, Villages villages, long gameTime) {
        if (gameTime % DISCOVERY_SPACING != 0) return;
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) return;
        ServerPlayer player = players.get(Math.floorMod(villages.discoveryCursor++, players.size()));
        if (player.isSpectator()) return;
        discover(level, villages, player.blockPosition(), DISCOVERY_CHUNK_RADIUS, gameTime);
    }

    /**
     * Looks for bells no village has yet in the loaded chunks within {@code chunkRadius} of {@code origin},
     * and adds each one with a bed near it: it founds a village, joins one or merges several. Returns how
     * many bells were added.
     */
    public static int discover(ServerLevel level, Villages villages, BlockPos origin, int chunkRadius, long gameTime) {
        PoiManager poi = level.getPoiManager();
        LongArrayList bells = new LongArrayList();
        int originX = origin.getX() >> 4;
        int originZ = origin.getZ() >> 4;
        for (int x = originX - chunkRadius; x <= originX + chunkRadius; x++) {
            for (int z = originZ - chunkRadius; z <= originZ + chunkRadius; z++) {
                if (!level.hasChunk(x, z)) continue;
                poi.getInChunk(BELL, new ChunkPos(x, z), PoiManager.Occupancy.ANY)
                        .forEach(record -> bells.add(record.getPos().asLong()));
            }
        }
        VillageStats.recordDiscovery();

        int added = 0;
        for (int i = 0; i < bells.size(); i++) {
            long bell = bells.getLong(i);
            if (villages.byAnchor(bell) != null) continue;
            BlockPos pos = BlockPos.of(bell);
            if (count(level, pos, Village.LINK_DISTANCE, BED) == 0) continue;
            if (villages.addAnchor(pos, gameTime) != null) added++;
        }
        return added;
    }

    /** POIs of {@code type} within {@code radius} of {@code center}, horizontally, in loaded chunks only. */
    static int count(ServerLevel level, BlockPos center, int radius, Predicate<Holder<PoiType>> type) {
        PoiManager poi = level.getPoiManager();
        int cx = center.getX();
        int cz = center.getZ();
        long limit = (long) radius * radius;
        int total = 0;
        for (int x = (cx - radius) >> 4; x <= (cx + radius) >> 4; x++) {
            for (int z = (cz - radius) >> 4; z <= (cz + radius) >> 4; z++) {
                if (!level.hasChunk(x, z)) continue;
                total += (int) poi.getInChunk(type, new ChunkPos(x, z), PoiManager.Occupancy.ANY)
                        .filter(record -> {
                            long dx = record.getPos().getX() - cx;
                            long dz = record.getPos().getZ() - cz;
                            return dx * dx + dz * dz <= limit;
                        })
                        .count();
            }
        }
        return total;
    }

    /** Whether every chunk within {@code radius} of {@code center} is loaded. */
    static boolean allChunksLoaded(ServerLevel level, BlockPos center, int radius) {
        int cx = center.getX();
        int cz = center.getZ();
        for (int x = (cx - radius) >> 4; x <= (cx + radius) >> 4; x++) {
            for (int z = (cz - radius) >> 4; z <= (cz + radius) >> 4; z++) {
                if (!level.hasChunk(x, z)) return false;
            }
        }
        return true;
    }

    /** Whether the entities of every chunk within {@code radius} of {@code center} are loaded. */
    static boolean allEntitiesLoaded(ServerLevel level, BlockPos center, int radius) {
        int cx = center.getX();
        int cz = center.getZ();
        for (int x = (cx - radius) >> 4; x <= (cx + radius) >> 4; x++) {
            for (int z = (cz - radius) >> 4; z <= (cz + radius) >> 4; z++) {
                if (!level.areEntitiesLoaded(VillageIndex.chunkKey(x, z))) return false;
            }
        }
        return true;
    }
}
