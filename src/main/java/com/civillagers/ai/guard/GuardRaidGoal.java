package com.civillagers.ai.guard;

import com.civillagers.ai.GuardPathBudget;
import com.civillagers.entity.guard.Guard;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;

import java.util.EnumSet;

/**
 * During a raid on the village, sends an armed guard with nothing to fight after the raiders themselves,
 * wherever they are: they come in from the edge of the village and go from house to house, while the
 * raid's center is the village's middle, by the bell. Each guard goes for the raider nearest it, so the
 * guards spread over the raiders instead of gathering in one place.
 *
 * <p>A raider within {@link #ENGAGE_RANGE} becomes the guard's target outright, seen or not (it may be
 * behind a house), and the fight goals take over. One farther away, within {@link #HUNT_RANGE}, the guard
 * walks towards. With no raider in reach (a wave not yet come) it waits near the center, as before.
 *
 * <p>Cost: the raid is looked up and its raiders, a few dozen at most, are read at most once every
 * {@link #LOOK_INTERVAL} ticks; each walk is a path from {@link GuardPathBudget}, searched again only
 * when the last ended or the raider moved on.
 */
public final class GuardRaidGoal extends Goal {
    private static final double SPEED = 0.7;
    /** A raider this close, in blocks, is the guard's target at once. */
    public static final double ENGAGE_RANGE = 24.0;
    /** How far, in blocks, a guard goes looking for raiders during a raid. */
    public static final double HUNT_RANGE = 96.0;
    /** Close enough to the center while no raider is in reach. */
    private static final double NEAR_CENTER_SQR = 12.0 * 12.0;
    /** Ticks between two looks at the raiders. */
    public static final int LOOK_INTERVAL = 20;
    private static final int REPATH = 40;

    private final Guard guard;
    private int nextLook;
    private int repath;
    /** Where the guard is walking: a raider's position or the center; never an entity. */
    private final BlockPos.MutableBlockPos goal = new BlockPos.MutableBlockPos();
    private boolean hasGoal;

    public GuardRaidGoal(Guard guard) {
        this.guard = guard;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (guard.getTarget() != null || !guard.isCombatant() || guard.isSleeping() || guard.raidCenter() == null) return false;
        if (guard.tickCount < nextLook) return false;
        nextLook = guard.tickCount + LOOK_INTERVAL;
        return look();
    }

    @Override
    public boolean canContinueToUse() {
        if (guard.getTarget() != null || !guard.isCombatant() || guard.raidCenter() == null) return false;
        if (guard.tickCount >= nextLook) {
            nextLook = guard.tickCount + LOOK_INTERVAL;
            return look();
        }
        return hasGoal;
    }

    @Override
    public void start() {
        repath = 0;
    }

    @Override
    public void tick() {
        ServerLevel level = guard.serverLevel();
        if (level == null || !hasGoal) return;
        // Ticked every other server tick, so each call stands for two.
        repath -= 2;
        if (repath > 0 && !guard.getNavigation().isDone()) return;
        if (!GuardPathBudget.tryTake(level.getGameTime())) return;
        repath = REPATH;
        guard.getNavigation().moveTo(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5, SPEED);
    }

    @Override
    public void stop() {
        hasGoal = false;
        guard.getNavigation().stop();
    }

    /**
     * Looks at the raid: targets the nearest raider if it is within {@link #ENGAGE_RANGE}, or sets
     * {@link #goal} to it, or to the center while none is in reach. Returns whether there is somewhere to
     * walk.
     */
    private boolean look() {
        ServerLevel level = guard.serverLevel();
        BlockPos center = guard.raidCenter();
        if (level == null || center == null) return hasGoal = false;
        Raid raid = level.getRaidAt(guard.blockPosition());
        Raider nearest = raid == null ? null : nearestRaider(guard, raid.getAllRaiders());
        if (nearest != null) {
            if (guard.distanceToSqr(nearest) <= ENGAGE_RANGE * ENGAGE_RANGE) {
                guard.setTarget(nearest);
                return hasGoal = false;
            }
            BlockPos at = nearest.blockPosition();
            if (hasGoal && goal.distSqr(at) > 4.0 * 4.0) repath = 0;
            goal.set(at);
            return hasGoal = true;
        }
        if (guard.blockPosition().distSqr(center) <= NEAR_CENTER_SQR) return hasGoal = false;
        goal.set(center);
        return hasGoal = true;
    }

    /** The living raider of {@code raiders} nearest {@code guard}, within {@link #HUNT_RANGE} and its level, or null. Public for the gametests. */
    public static Raider nearestRaider(Guard guard, Iterable<? extends Raider> raiders) {
        Raider nearest = null;
        double nearestDistance = HUNT_RANGE * HUNT_RANGE;
        for (Raider raider : raiders) {
            if (!raider.isAlive() || raider.level() != guard.level() || raider.isInvisible()) continue;
            double distance = guard.distanceToSqr(raider);
            if (distance < nearestDistance) {
                nearest = raider;
                nearestDistance = distance;
            }
        }
        return nearest;
    }
}
