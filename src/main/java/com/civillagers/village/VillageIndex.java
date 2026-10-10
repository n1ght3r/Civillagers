package com.civillagers.village;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

/**
 * Which village covers each chunk, so "which village is this position in" is a hash lookup rather than a
 * loop over every village. Each chunk a village's radius touches maps to the village whose center is
 * nearest; the rare chunk two villages share keeps the others aside.
 *
 * <p>Runtime only: {@link Villages} rebuilds it from the villages whenever their bells change, which is
 * rare, so it is never updated piecemeal and never saved.
 */
public final class VillageIndex {
    private final Long2IntOpenHashMap nearest = new Long2IntOpenHashMap();
    private final Long2ObjectOpenHashMap<IntArrayList> others = new Long2ObjectOpenHashMap<>();

    VillageIndex() {
        nearest.defaultReturnValue(0);
    }

    /** Packs chunk coordinates the way Minecraft does, so a key works with {@code ServerLevel} lookups. */
    public static long chunkKey(int chunkX, int chunkZ) {
        return (chunkX & 0xFFFFFFFFL) | ((chunkZ & 0xFFFFFFFFL) << 32);
    }

    /** The id of the village nearest the chunk among those covering it, or 0. */
    public int villageAt(int chunkX, int chunkZ) {
        return nearest.get(chunkKey(chunkX, chunkZ));
    }

    /** The other villages covering the chunk, or null; usually null. */
    IntArrayList othersAt(int chunkX, int chunkZ) {
        return others.get(chunkKey(chunkX, chunkZ));
    }

    /** How many chunks are covered. */
    public int size() {
        return nearest.size();
    }

    void rebuild(Int2ObjectMap<Village> villages) {
        nearest.clear();
        others.clear();
        for (Village village : villages.values()) {
            int cx = village.center().getX();
            int cz = village.center().getZ();
            int r = village.radius();
            int minX = (cx - r) >> 4, maxX = (cx + r) >> 4;
            int minZ = (cz - r) >> 4, maxZ = (cz + r) >> 4;
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (!touches(village, x, z)) continue;
                    long key = chunkKey(x, z);
                    int current = nearest.get(key);
                    if (current == 0) {
                        nearest.put(key, village.id());
                        continue;
                    }
                    Village other = villages.get(current);
                    int loser = village.id();
                    if (other == null || distanceSq(village, x, z) < distanceSq(other, x, z)) {
                        nearest.put(key, village.id());
                        loser = current;
                    }
                    others.computeIfAbsent(key, k -> new IntArrayList(2)).add(loser);
                }
            }
        }
    }

    /** Whether the village's circle reaches into the chunk. */
    private static boolean touches(Village village, int chunkX, int chunkZ) {
        int cx = village.center().getX();
        int cz = village.center().getZ();
        int nearestX = Math.max(chunkX << 4, Math.min(cx, (chunkX << 4) + 15));
        int nearestZ = Math.max(chunkZ << 4, Math.min(cz, (chunkZ << 4) + 15));
        return village.contains(nearestX, nearestZ);
    }

    private static long distanceSq(Village village, int chunkX, int chunkZ) {
        long dx = (chunkX << 4) + 8 - village.center().getX();
        long dz = (chunkZ << 4) + 8 - village.center().getZ();
        return dx * dx + dz * dz;
    }
}
