package com.civillagers.ai.guard;

import com.civillagers.ai.GuardPathBudget;
import com.civillagers.ai.villager.CivHideGoal;
import com.civillagers.entity.guard.Guard;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * When a bell rings ({@link Guard#alarmBell()}), an armed guard with nothing to fight runs to it while the
 * villagers hide, and stands by it until the alarm is over; the target goal picks up whatever comes. A
 * guard with no weapon hides instead ({@link CivHideGoal}). During a raid the bell is left alone: the guards
 * go after the raiders where they are ({@link GuardRaidGoal}), not where the bell is.
 *
 * <p>A path from {@link GuardPathBudget}, searched again only when the last one ended short of the bell.
 */
public final class GuardRallyGoal extends Goal {
    private static final double SPEED = 0.7;
    /** Close enough to the bell, horizontally. */
    private static final double AT_BELL_SQR = 4.0 * 4.0;
    private static final int REPATH = 40;

    private final Guard guard;
    private int repath;

    public GuardRallyGoal(Guard guard) {
        this.guard = guard;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        // No bell, the common case, first: it is a field read.
        return guard.alarmBell() != null && guard.getTarget() == null && !guard.isSleeping() && guard.isCombatant()
                && guard.raidCenter() == null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        repath = 0;
    }

    @Override
    public void stop() {
        guard.getNavigation().stop();
    }

    @Override
    public void tick() {
        BlockPos bell = guard.alarmBell();
        if (bell == null) return;
        double dx = bell.getX() + 0.5 - guard.getX();
        double dz = bell.getZ() + 0.5 - guard.getZ();
        if (dx * dx + dz * dz <= AT_BELL_SQR) {
            guard.getNavigation().stop();
            return;
        }
        // Ticked every other server tick, so each call stands for two.
        repath -= 2;
        if (!guard.getNavigation().isDone() || repath > 0) return;
        ServerLevel level = guard.serverLevel();
        if (level == null || !GuardPathBudget.tryTake(level.getGameTime())) return;
        repath = REPATH;
        guard.getNavigation().moveTo(bell.getX() + 0.5, bell.getY(), bell.getZ() + 0.5, SPEED);
    }
}
