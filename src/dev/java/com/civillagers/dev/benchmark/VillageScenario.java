package com.civillagers.dev.benchmark;

import com.civillagers.village.VillageScheduler;
import com.civillagers.village.VillageTicker;
import com.civillagers.village.Villages;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

/**
 * What the village core costs with {@code count} villages: the mod's own {@link VillageTicker} over a set
 * of villages of the run's own, with real bells in loaded chunks, timed one simulated tick at a time. Each
 * village is one bell with nothing around it, the "empty village" of the roadmap's phase 1 budget.
 *
 * <p>The villages are built straight from saved data rather than through {@link Villages#addAnchor}, so
 * they can sit a chunk apart without merging; how far apart they are does not change what an update
 * costs. Simulated ticks advance the clock the scheduler reads, many to a server tick, so a run covers
 * several update intervals in seconds.
 *
 * <p>With {@code unloaded} set the bells are far outside the loaded area instead: the cost of villages
 * nobody is near, which should be close to nothing.
 */
final class VillageScenario implements Stage {
    /** Where unloaded villages go: far beyond anything a run loads. */
    private static final int FAR = 20_000_000;

    private final BenchmarkWorld world;
    private final int count;
    private final int warmup;
    private final int measured;
    private final boolean unloaded;
    private final Metrics.Samples nanos = new Metrics.Samples();
    private Villages villages;
    private long clock;
    private int tick;
    private long updates;
    private long allocated;
    private JsonObject result;

    VillageScenario(BenchmarkWorld world, int count, int warmup, int measured, boolean unloaded) {
        this.world = world;
        this.count = count;
        this.warmup = warmup;
        this.measured = measured;
        this.unloaded = unloaded;
    }

    @Override
    public String name() {
        return (unloaded ? "unloaded villages x" : "villages x") + count;
    }

    @Override
    public String progress() {
        return tick + "/" + (warmup + measured) + " simulated ticks";
    }

    /** Places this scenario's bells, before the idle ticks that let their points of interest register. */
    void prepare() {
        JsonArray list = new JsonArray();
        for (int i = 0; i < count; i++) {
            BlockPos bell = unloaded ? new BlockPos(FAR + i * 64, 64, FAR) : world.villageBell(i);
            if (!unloaded) world.place(bell, Blocks.BELL.defaultBlockState());
            JsonObject village = new JsonObject();
            village.addProperty("id", i + 1);
            JsonArray anchors = new JsonArray();
            JsonArray pos = new JsonArray();
            pos.add(bell.getX());
            pos.add(bell.getY());
            pos.add(bell.getZ());
            anchors.add(pos);
            village.add("anchors", anchors);
            list.add(village);
        }
        JsonObject data = new JsonObject();
        data.addProperty("next_id", count + 1);
        data.add("villages", list);
        villages = Villages.CODEC.parse(JsonOps.INSTANCE, data).getOrThrow();
        if (villages.size() != count) throw new IllegalStateException(name() + " built " + villages.size() + " villages");
        clock = world.level.getGameTime() + 1_000_000L;
    }

    @Override
    public boolean run(long deadlineNanos) {
        if (villages == null) prepare();
        while (tick < warmup + measured) {
            long allocatedBefore = Metrics.allocatedBytes();
            long start = System.nanoTime();
            int updated = VillageTicker.tickLevel(world.level, villages, clock++);
            long elapsed = System.nanoTime() - start;
            if (tick >= warmup) {
                nanos.add(elapsed);
                updates += updated;
                allocated += Metrics.allocatedBytes() - allocatedBefore;
            }
            tick++;
            if (System.nanoTime() >= deadlineNanos) break;
        }
        if (tick < warmup + measured) return false;
        finish();
        return true;
    }

    private void finish() {
        // Every village must still be there: a bell the update could not find would have disbanded one,
        // and the scenario would be measuring fewer villages than it says.
        if (villages.size() != count) {
            throw new IllegalStateException(name() + " lost villages during the run: " + villages.size() + " left");
        }
        if (!unloaded && updates == 0) throw new IllegalStateException(name() + " ran no village update");
        JsonObject json = new JsonObject();
        json.addProperty("villages", count);
        json.addProperty("unloaded", unloaded);
        json.addProperty("measuredTicks", nanos.size());
        json.add("msPerTick", nanos.summary(1_000_000.0));
        json.addProperty("updates", updates);
        json.addProperty("updatesPerVillagePerInterval",
                Metrics.round(updates * (double) VillageScheduler.INTERVAL / ((double) count * nanos.size())));
        json.addProperty("microsPerUpdate", updates == 0 ? 0.0 : Metrics.round(nanos.sum() / 1000.0 / updates));
        json.addProperty("allocatedBytesPerTick", Metrics.round(allocated / (double) nanos.size()));
        result = json;
        villages = null;
    }

    JsonObject result() {
        return result;
    }
}
