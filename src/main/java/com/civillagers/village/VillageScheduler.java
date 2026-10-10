package com.civillagers.village;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntComparators;
import net.minecraft.server.level.ServerLevel;

/**
 * Runs each village's update once every {@link #INTERVAL} ticks, staggered and within a budget.
 *
 * <ul>
 *   <li><b>Staggered.</b> Village {@code id} falls due on the ticks where
 *       {@code (gameTime - slot(id)) % INTERVAL == 0}, with the slot a hash of the id, so villages do not
 *       all land on the same tick.</li>
 *   <li><b>Budgeted.</b> A tick runs at most {@link #maxPerTick()} updates and stops once
 *       {@link #budgetNanos()} has gone by. A village that does not fit waits in the queue for the next
 *       tick; at least one update always runs, so a slow village cannot starve the rest.</li>
 *   <li><b>Loaded only.</b> A village whose center chunk is not loaded is skipped until its next slot and
 *       costs a hash lookup.</li>
 * </ul>
 *
 * <p>One per {@link Villages}, so per level; nothing here is static but the settings. The tick loop
 * allocates nothing.
 */
public final class VillageScheduler {
    /** Ticks between two updates of one village: 5 seconds. */
    public static final int INTERVAL = 100;

    private static final int MAX_PER_TICK = Integer.getInteger("civillagers.scheduler.maxPerTick", 8);
    private static final long BUDGET_NANOS =
            (long) (Double.parseDouble(System.getProperty("civillagers.scheduler.budgetMs", "0.5")) * 1_000_000L);

    /** What the scheduler runs. {@link VillageTicker} in the game; tests pass their own. */
    public interface Work {
        /** Whether the village's chunks are loaded enough to update it now. */
        boolean canUpdate(ServerLevel level, Village village);

        /** One update. It may merge, split or disband villages. */
        void update(ServerLevel level, Villages villages, Village village, long gameTime);
    }

    private final IntArrayList[] slots = new IntArrayList[INTERVAL];
    private final IntArrayFIFOQueue due = new IntArrayFIFOQueue();
    private boolean slotsStale = true;

    // What the last tick did, for the debug command and the tests.
    private int lastUpdated;
    private int lastSkipped;

    VillageScheduler() {
        for (int i = 0; i < INTERVAL; i++) slots[i] = new IntArrayList();
    }

    public static int maxPerTick() {
        return MAX_PER_TICK;
    }

    public static long budgetNanos() {
        return BUDGET_NANOS;
    }

    /** The tick offset, in {@code [0, INTERVAL)}, of the village with this id. */
    public static int slot(int villageId) {
        return Math.floorMod(HashCommon.mix(villageId), INTERVAL);
    }

    /** Villages were added or removed; the slots are rebuilt on the next tick. */
    void invalidate() {
        slotsStale = true;
    }

    /** Villages waiting for their update. */
    public int queued() {
        return due.size();
    }

    public int lastUpdated() {
        return lastUpdated;
    }

    public int lastSkipped() {
        return lastSkipped;
    }

    /** One tick: queue the villages that fall due now, then update as many as the budget allows. */
    public void tick(ServerLevel level, Villages villages, long gameTime, Work work) {
        if (slotsStale) rebuildSlots(villages);
        IntArrayList now = slots[Math.floorMod(gameTime, INTERVAL)];
        for (int i = 0; i < now.size(); i++) {
            Village village = villages.byId(now.getInt(i));
            if (village != null && !village.queued) {
                village.queued = true;
                due.enqueue(village.id());
            }
        }

        lastUpdated = 0;
        lastSkipped = 0;
        if (due.isEmpty()) return;
        long start = System.nanoTime();
        while (!due.isEmpty() && lastUpdated < MAX_PER_TICK
                && (lastUpdated == 0 || System.nanoTime() - start < BUDGET_NANOS)) {
            Village village = villages.byId(due.dequeueInt());
            if (village == null) continue;
            village.queued = false;
            if (!work.canUpdate(level, village)) {
                lastSkipped++;
                continue;
            }
            village.lastTicked = gameTime;
            village.updates++;
            work.update(level, villages, village, gameTime);
            lastUpdated++;
        }
    }

    private void rebuildSlots(Villages villages) {
        slotsStale = false;
        for (IntArrayList slot : slots) slot.clear();
        for (Village village : villages.all()) slots[slot(village.id())].add(village.id());
        // Same order every time, whatever the hash map's.
        for (IntArrayList slot : slots) {
            if (slot.size() > 1) slot.sort(IntComparators.NATURAL_COMPARATOR);
        }
    }
}
