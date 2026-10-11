package com.civillagers.ai.guard;

import com.civillagers.ai.GuardPathBudget;
import com.civillagers.ai.Homes;
import com.civillagers.entity.guard.Guard;
import com.civillagers.platform.Vanilla;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;

/**
 * Sends a guard to bed at night and wakes it at dawn. The bed is the nearest free one: no villager has
 * claimed it, and nobody is lying in it. A guard does not claim a bed for good, so it never takes one
 * from a villager; it finds one each night, at most once every {@link #SEARCH_INTERVAL} ticks while it
 * has none, and only in loaded chunks.
 *
 * <p>A guard stays on its feet while it has a target or a raid is near, and something that turns up
 * while it sleeps wakes it: the target goal keeps looking while the guard lies in bed, and this goal
 * gives way to the fight.
 */
public final class GuardSleepGoal extends Goal {
    /** Ticks between two bed searches while a guard has none. */
    static final int SEARCH_INTERVAL = 100;
    /** How far a guard looks for a bed, in blocks: two chunks either way. */
    static final int SEARCH_RADIUS = 32;
    private static final double SPEED = 0.5;
    /** A guard lies down once it is this close to its bed, horizontally. */
    private static final double REACH_SQR = 2.0 * 2.0;
    /** Ticks a guard keeps walking to a bed it cannot reach before giving up on it for the night. */
    private static final int GIVE_UP = 400;
    private static final int REPATH = 40;

    private final Guard guard;
    private BlockPos bed;
    private int nextSearch;
    private int walking;
    private int repath;

    public GuardSleepGoal(Guard guard) {
        this.guard = guard;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        ServerLevel level = guard.serverLevel();
        if (level == null || !wantsToSleep(level) || guard.tickCount < nextSearch) return false;
        nextSearch = guard.tickCount + SEARCH_INTERVAL;
        bed = findBed(level, guard.blockPosition(), SEARCH_RADIUS);
        return bed != null;
    }

    @Override
    public boolean canContinueToUse() {
        ServerLevel level = guard.serverLevel();
        if (level == null || bed == null || !wantsToSleep(level) || walking > GIVE_UP) return false;
        BlockState state = level.getBlockState(bed);
        return state.getBlock() instanceof BedBlock && (guard.isSleeping() || !state.getValue(BedBlock.OCCUPIED));
    }

    @Override
    public void start() {
        walking = 0;
        repath = 0;
    }

    @Override
    public void stop() {
        if (guard.isSleeping()) guard.stopSleeping();
        if (walking > GIVE_UP) nextSearch = guard.tickCount + GIVE_UP;
        bed = null;
        guard.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (guard.isSleeping() || bed == null) return;
        // Ticked every other server tick, so each call stands for two.
        walking += 2;
        double dx = bed.getX() + 0.5 - guard.getX();
        double dz = bed.getZ() + 0.5 - guard.getZ();
        if (dx * dx + dz * dz <= REACH_SQR && Math.abs(bed.getY() - guard.getY()) < 2) {
            guard.getNavigation().stop();
            guard.startSleeping(bed);
            return;
        }
        repath -= 2;
        if (guard.getNavigation().isDone() && repath <= 0) {
            ServerLevel level = guard.serverLevel();
            if (level == null || !GuardPathBudget.tryTake(level.getGameTime())) return;
            repath = REPATH;
            guard.getNavigation().moveTo(bed.getX() + 0.5, bed.getY(), bed.getZ() + 0.5, SPEED);
        }
    }

    private boolean wantsToSleep(ServerLevel level) {
        return Vanilla.isNight(level) && guard.getTarget() == null && guard.raidCenter() == null;
    }

    /**
     * The nearest bed within {@code radius} of {@code origin} that no villager has claimed and nobody lies
     * in, or null. See {@link Homes#nearestBed}.
     */
    public static BlockPos findBed(ServerLevel level, BlockPos origin, int radius) {
        return Homes.nearestBed(level, origin, radius, true);
    }
}
