package com.civillagers.village;

import com.civillagers.entity.Guard;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.UUID;

/**
 * The server tick for villages, and one village's update, which {@link VillageScheduler} runs every
 * {@link VillageScheduler#INTERVAL} ticks for each village whose center chunk is loaded.
 *
 * <p>An update checks its bells first, every time: a bell that is gone leaves the village, which may split
 * or disband it. The slower checks take turns, one per update, so a village never does them all at once:
 * recounting beds and workstations, and finding which villagers and guards live there.
 */
public final class VillageTicker implements VillageScheduler.Work {
    public static final VillageTicker INSTANCE = new VillageTicker();

    /** Vertical reach of the villager scan above and below the center. */
    private static final int MEMBER_SCAN_HEIGHT = 32;

    /** Reused for POI lookups, main thread only. */
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    private VillageTicker() { }

    /** The end of every server tick: each level's discovery and village updates, timed. */
    public static void onServerTick(MinecraftServer server) {
        long start = System.nanoTime();
        int updated = 0;
        for (ServerLevel level : server.getAllLevels()) {
            updated += tickLevel(level, Villages.get(level), level.getGameTime());
        }
        VillageStats.recordTick(System.nanoTime() - start, updated);
    }

    /** One level's tick. Returns how many villages were updated. */
    public static int tickLevel(ServerLevel level, Villages villages, long gameTime) {
        VillageDetector.discoverNearPlayers(level, villages, gameTime);
        if (villages.isEmpty()) return 0;
        VillageScheduler scheduler = villages.scheduler();
        scheduler.tick(level, villages, gameTime, INSTANCE);
        return scheduler.lastUpdated();
    }

    @Override
    public boolean canUpdate(ServerLevel level, Village village) {
        return level.hasChunk(village.center().getX() >> 4, village.center().getZ() >> 4);
    }

    @Override
    public void update(ServerLevel level, Villages villages, Village village, long gameTime) {
        if (!checkBells(level, villages, village, gameTime)) return;
        switch (village.updates % 4) {
            case 1 -> countPois(level, villages, village);
            case 3 -> findMembers(level, villages, village);
            default -> { }
        }
    }

    /**
     * Drops the first bell found missing, in a loaded chunk. Returns false when one was: the village may
     * have split or been disbanded, and the rest waits for its next update.
     */
    private boolean checkBells(ServerLevel level, Villages villages, Village village, long gameTime) {
        PoiManager poi = level.getPoiManager();
        for (int i = 0; i < village.anchorCount(); i++) {
            long anchor = village.anchorAt(i);
            if (!level.hasChunk(BlockPos.getX(anchor) >> 4, BlockPos.getZ(anchor) >> 4)) continue;
            if (poi.existsAtPosition(PoiTypes.MEETING, cursor.set(anchor))) continue;
            villages.removeAnchor(village, anchor, gameTime);
            return false;
        }
        return true;
    }

    /**
     * Recounts beds and workstations. With part of the village unloaded the count can only go up: what
     * cannot be seen is not assumed gone.
     */
    private static void countPois(ServerLevel level, Villages villages, Village village) {
        int beds = VillageDetector.count(level, village.center(), village.radius(), VillageDetector.BED);
        int workstations = VillageDetector.count(level, village.center(), village.radius(), VillageDetector.WORKSTATION);
        if (!VillageDetector.allChunksLoaded(level, village.center(), village.radius())) {
            beds = Math.max(beds, village.beds());
            workstations = Math.max(workstations, village.workstations());
        }
        if (village.setCounts(beds, workstations)) villages.contentChanged();
    }

    /**
     * Finds the villagers and guards within the village. With every chunk's entities loaded, they are the
     * members and the guards; otherwise they join and nobody leaves, since one out of sight may only be
     * unloaded.
     */
    private static void findMembers(ServerLevel level, Villages villages, Village village) {
        BlockPos center = village.center();
        int r = village.radius();
        AABB box = new AABB(center.getX() - r, center.getY() - MEMBER_SCAN_HEIGHT, center.getZ() - r,
                center.getX() + r + 1, center.getY() + MEMBER_SCAN_HEIGHT, center.getZ() + r + 1);
        List<Villager> villagers = level.getEntitiesOfClass(Villager.class, box,
                villager -> villager.isAlive() && village.contains(villager.blockPosition()));
        List<Guard> guards = level.getEntitiesOfClass(Guard.class, box,
                guard -> guard.isAlive() && village.contains(guard.blockPosition()));
        boolean changed = false;
        if (VillageDetector.allEntitiesLoaded(level, center, r)) {
            ObjectLinkedOpenHashSet<UUID> ids = new ObjectLinkedOpenHashSet<>(villagers.size());
            for (Villager villager : villagers) ids.add(villager.getUUID());
            changed |= village.replaceMembers(ids);
            ids = new ObjectLinkedOpenHashSet<>(guards.size());
            for (Guard guard : guards) ids.add(guard.getUUID());
            changed |= village.replaceGuards(ids);
        } else {
            for (Villager villager : villagers) changed |= village.addMember(villager.getUUID());
            for (Guard guard : guards) changed |= village.addGuard(guard.getUUID());
        }
        if (changed) villages.contentChanged();
    }
}
