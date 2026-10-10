package com.civillagers.dev.benchmark;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/**
 * The time entities of one watched type spend in their own tick, summed per server tick, measured from the
 * outside by the dev mod's {@code ServerLevelMixin}, so a guard and a vanilla mob are timed the same way.
 * Off unless a scenario watches a type; server thread only.
 */
public final class EntityTickTimer {
    private static EntityType<?> watched;
    private static long started;
    private static long tickNanos;
    private static int tickEntities;

    private EntityTickTimer() { }

    /** Starts timing the entities of {@code type}, or stops with null. */
    static void watch(EntityType<?> type) {
        watched = type;
        tickNanos = 0;
        tickEntities = 0;
    }

    public static void begin(Entity entity) {
        if (watched != null && entity.getType() == watched) started = System.nanoTime();
    }

    public static void end(Entity entity) {
        if (watched == null || entity.getType() != watched) return;
        tickNanos += System.nanoTime() - started;
        tickEntities++;
    }

    /** What the watched entities' ticks cost since the last call, in nanoseconds; starts the count again. */
    static long drainNanos() {
        long nanos = tickNanos;
        tickNanos = 0;
        return nanos;
    }

    /** How many watched entities ticked since the last call; starts the count again. */
    static int drainEntities() {
        int entities = tickEntities;
        tickEntities = 0;
        return entities;
    }
}
