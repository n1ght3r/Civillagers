package com.civillagers.ai;

import com.civillagers.entity.Guard;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

/**
 * Closes in on the target and strikes it with whatever the main hand holds, unless that is a bow, which
 * {@link GuardBowGoal} shoots instead. Vanilla's melee goal already spaces out its path recomputation, by
 * more when the target is far away, so this only adds the bow check.
 */
public final class GuardMeleeGoal extends MeleeAttackGoal {
    /** Faster than a patrol: a guard runs to a fight. */
    static final double SPEED = 0.75;

    private final Guard guard;

    public GuardMeleeGoal(Guard guard) {
        super(guard, SPEED, true);
        this.guard = guard;
    }

    @Override
    public boolean canUse() {
        return !guard.holdsBow() && super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return !guard.holdsBow() && super.canContinueToUse();
    }
}
