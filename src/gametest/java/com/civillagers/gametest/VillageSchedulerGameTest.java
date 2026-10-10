package com.civillagers.gametest;

import com.civillagers.Civillagers;
import com.civillagers.village.Village;
import com.civillagers.village.VillageScheduler;
import com.civillagers.village.VillageStats;
import com.civillagers.village.Villages;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;

/** The scheduler staggers updates, keeps to its per-tick cap, carries the rest over and skips unloaded villages. */
public final class VillageSchedulerGameTest {
    /** Counts updates instead of doing them. A village counts as loaded unless its x is negative. */
    private static final class Counting implements VillageScheduler.Work {
        final Int2IntOpenHashMap updates = new Int2IntOpenHashMap();

        @Override
        public boolean canUpdate(ServerLevel level, Village village) {
            return village.center().getX() >= 0;
        }

        @Override
        public void update(ServerLevel level, Villages villages, Village village, long gameTime) {
            updates.addTo(village.id(), 1);
        }
    }

    /** {@code count} villages far enough apart never to merge, all at x >= 0. */
    private static Villages spread(int count) {
        Villages villages = new Villages();
        for (int i = 0; i < count; i++) villages.addAnchor(new BlockPos((i % 40) * 200, 64, (i / 40) * 200), 0L);
        return villages;
    }

    @GameTest
    public void everyVillageUpdatesOncePerInterval(GameTestHelper helper) {
        Villages villages = spread(50);
        TestFixtures.check(helper, villages.size() == 50, "50 separate villages, not " + villages.size());
        Counting work = new Counting();
        for (long t = 0; t < VillageScheduler.INTERVAL; t++) villages.scheduler().tick(helper.getLevel(), villages, t, work);
        for (Village village : villages.all()) {
            TestFixtures.check(helper, work.updates.get(village.id()) == 1,
                    village + " should update once per interval, not " + work.updates.get(village.id()));
        }
        helper.succeed();
    }

    @GameTest
    public void updatesAreStaggered(GameTestHelper helper) {
        Villages villages = spread(50);
        Counting work = new Counting();
        int busiest = 0;
        for (long t = 0; t < VillageScheduler.INTERVAL; t++) {
            villages.scheduler().tick(helper.getLevel(), villages, t, work);
            busiest = Math.max(busiest, villages.scheduler().lastUpdated());
        }
        TestFixtures.check(helper, busiest < 10, "50 villages should spread over the interval, but one tick ran " + busiest);
        helper.succeed();
    }

    @GameTest
    public void aTickKeepsToItsCapAndCarriesTheRestOver(GameTestHelper helper) {
        Villages villages = spread(1200);
        Counting work = new Counting();
        VillageScheduler scheduler = villages.scheduler();
        for (long t = 0; t < VillageScheduler.INTERVAL; t++) {
            scheduler.tick(helper.getLevel(), villages, t, work);
            TestFixtures.check(helper, scheduler.lastUpdated() <= VillageScheduler.maxPerTick(),
                    "a tick ran " + scheduler.lastUpdated() + " updates, over the cap");
        }
        TestFixtures.check(helper, scheduler.queued() > 0, "1200 villages cannot all fit in one interval at the cap");
        int total = 0;
        for (int count : work.updates.values()) total += count;
        TestFixtures.check(helper, total + scheduler.queued() == villages.size(),
                "every village is either updated or still waiting: " + total + " + " + scheduler.queued());
        helper.succeed();
    }

    @GameTest
    public void unloadedVillagesAreSkipped(GameTestHelper helper) {
        Villages villages = new Villages();
        Village loaded = villages.addAnchor(new BlockPos(10, 64, 10), 0L);
        Village unloaded = villages.addAnchor(new BlockPos(-10_000, 64, 10), 0L);
        Counting work = new Counting();
        int skipped = 0;
        for (long t = 0; t < VillageScheduler.INTERVAL; t++) {
            villages.scheduler().tick(helper.getLevel(), villages, t, work);
            skipped += villages.scheduler().lastSkipped();
        }
        TestFixtures.check(helper, work.updates.get(loaded.id()) == 1, "the loaded village updates");
        TestFixtures.check(helper, work.updates.get(unloaded.id()) == 0, "the unloaded one does not");
        TestFixtures.check(helper, skipped == 1, "and is skipped once, at its slot, not " + skipped);
        TestFixtures.check(helper, villages.scheduler().queued() == 0, "a skipped village does not stay queued");
        helper.succeed();
    }

    @GameTest
    public void stoppingTheServerClearsEveryCache(GameTestHelper helper) {
        VillageStats.recordTick(1000L, 1);
        TestFixtures.check(helper, Civillagers.cachedEntries() > 0, "a recorded tick is cached");
        Civillagers.clearCaches();
        TestFixtures.check(helper, Civillagers.cachedEntries() == 0, "clearing leaves nothing behind");
        TestFixtures.check(helper, VillageStats.updates() == 0 && VillageStats.maxMillis() == 0, "every counter is reset");
        helper.succeed();
    }
}
