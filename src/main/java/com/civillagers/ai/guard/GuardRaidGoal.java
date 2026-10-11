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
 * During a raid, sends an armed guard with nothing to fight after the raiders in its village, as an iron
 * golem defends its village: it never goes out to meet the raid, it meets the raiders where they come in,
 * at the houses, and it goes first for those attacking villagers.
 *
 * <p>Which raider: one inside its home village ({@link Guard#nearHome}), or within {@link #ENGAGE_RANGE}
 * of the guard; the nearest, with one going after a villager counting as half as far. One within
 * {@link #ENGAGE_RANGE} becomes the guard's target outright, seen or not (it may be behind a house), and
 * the fight goals take over; one farther in the village the guard walks towards. With none in reach the
 * guard goes on patrolling: it does not gather at the bell, the raid's center.
 *
 * <p>Cost: the raid is looked up and its raiders, a few dozen at most, are read at most once every
 * {@link #LOOK_INTERVAL} ticks; each walk is a path from {@link GuardPathBudget}, searched again only
 * when the last ended or the raider moved on.
 */
public final class GuardRaidGoal extends Goal {
    /** A raider this close, in blocks, is the guard's target at once. */
    public static final double ENGAGE_RANGE = 16.0;
    /** Ticks between two looks at the raiders. */
    public static final int LOOK_INTERVAL = 20;
    /** A raider going after a villager counts as this much nearer, squared. */
    private static final double DEFENDS_VILLAGERS = 0.25;
    private static final int REPATH = 40;

    private final Guard guard;
    private int nextLook;
    private int repath;
    /** Where the guard is walking: a raider's position; never the entity. */
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
        guard.getNavigation().moveTo(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5, guard.runSpeed());
    }

    @Override
    public void stop() {
        hasGoal = false;
        guard.getNavigation().stop();
    }

    /**
     * Looks at the raid: targets the chosen raider if it is within {@link #ENGAGE_RANGE}, or sets
     * {@link #goal} to it. Returns whether there is somewhere to walk.
     */
    private boolean look() {
        ServerLevel level = guard.serverLevel();
        if (level == null) return hasGoal = false;
        Raid raid = level.getRaidAt(guard.blockPosition());
        Raider raider = raid == null ? null : nearestRaider(guard, raid.getAllRaiders());
        if (raider == null) return hasGoal = false;
        if (guard.distanceToSqr(raider) <= ENGAGE_RANGE * ENGAGE_RANGE) {
            guard.setTarget(raider);
            return hasGoal = false;
        }
        BlockPos at = raider.blockPosition();
        if (hasGoal && goal.distSqr(at) > 4.0 * 4.0) repath = 0;
        goal.set(at);
        return hasGoal = true;
    }

    /**
     * The raider of {@code raiders} the guard goes for, or null: a living one in its level, inside its home
     * village or within {@link #ENGAGE_RANGE} of it, not one it just gave up on as out of reach; the
     * nearest, a raider going after a villager counting as half as far. Public for the gametests.
     */
    public static Raider nearestRaider(Guard guard, Iterable<? extends Raider> raiders) {
        Raider nearest = null;
        double nearestScore = Double.MAX_VALUE;
        for (Raider raider : raiders) {
            if (!raider.isAlive() || raider.level() != guard.level() || raider.isInvisible() || guard.gaveUpOn(raider)) continue;
            double distance = guard.distanceToSqr(raider);
            boolean close = distance <= ENGAGE_RANGE * ENGAGE_RANGE;
            if (!close && (!guard.hasHome() || !guard.nearHome(raider.getX(), raider.getZ(), 0.0))) continue;
            double score = distance * (Guard.attacksVillager(raider) ? DEFENDS_VILLAGERS : 1.0);
            if (score < nearestScore) {
                nearest = raider;
                nearestScore = score;
            }
        }
        return nearest;
    }
}
