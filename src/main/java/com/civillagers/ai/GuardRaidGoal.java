package com.civillagers.ai;

import com.civillagers.entity.Guard;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * During a raid on the village, sends a guard with nothing to fight towards the raid's center, where the
 * raiders come; the target goal picks them up from there. Whether a raid is near is
 * {@link Guard#raidCenter()}, which looks it up only now and then.
 */
public final class GuardRaidGoal extends Goal {
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

    @Override
    public boolean canUse() {
        if (guard.getTarget() != null) return false;
        BlockPos center = guard.raidCenter();
        return center != null && guard.blockPosition().distSqr(center) > NEAR_CENTER_SQR;
    }

    @Override
    public void start() {
        repath = 0;
    }

    @Override
    public void tick() {
        BlockPos center = guard.raidCenter();
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
