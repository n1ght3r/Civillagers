package com.civillagers.ai;

import com.civillagers.entity.Guard;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.raid.Raid;

import java.util.EnumSet;

/**
 * During a raid on the village, sends a guard with nothing to fight towards the raid's center, where the
 * raiders come; the target goal picks them up from there. Whether a raid is near is looked up at most once
 * every {@link #CHECK_INTERVAL} ticks per guard, and the answer also keeps the guard out of bed.
 */
public final class GuardRaidGoal extends Goal {
    /** Ticks between two raid lookups by one guard. */
    static final int CHECK_INTERVAL = 40;
    private static final double SPEED = 0.7;
    /** Close enough to the center: the raiders are within the guard's sight from here. */
    private static final double NEAR_CENTER_SQR = 12.0 * 12.0;
    private static final int REPATH = 40;

    private final Guard guard;
    private int repath;

    public GuardRaidGoal(Guard guard) {
        this.guard = guard;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    /** Whether an active raid covers where {@code guard} stands, as of its last lookup. */
    public static boolean raidNear(Guard guard) {
        return center(guard) != null;
    }

    /** The center of the active raid around {@code guard}, as of its last lookup, or null. */
    static BlockPos center(Guard guard) {
        ServerLevel level = guard.serverLevel();
        if (level == null) return null;
        if (guard.tickCount >= guard.raidCheckTick) {
            guard.raidCheckTick = guard.tickCount + CHECK_INTERVAL;
            Raid raid = level.getRaidAt(guard.blockPosition());
            guard.raidCenter = raid != null && raid.isActive() ? raid.getCenter() : null;
        }
        return guard.raidCenter;
    }

    @Override
    public boolean canUse() {
        if (guard.getTarget() != null) return false;
        BlockPos center = center(guard);
        return center != null && guard.blockPosition().distSqr(center) > NEAR_CENTER_SQR;
    }

    @Override
    public void start() {
        repath = 0;
    }

    @Override
    public void tick() {
        BlockPos center = guard.raidCenter;
        ServerLevel level = guard.serverLevel();
        if (center == null || level == null) return;
        repath -= 2;
        if (repath > 0 && !guard.getNavigation().isDone()) return;
        if (!GuardPathBudget.tryTake(level.getGameTime())) return;
        repath = REPATH;
        guard.getNavigation().moveTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5, SPEED);
    }

    @Override
    public void stop() {
        guard.getNavigation().stop();
    }
}
