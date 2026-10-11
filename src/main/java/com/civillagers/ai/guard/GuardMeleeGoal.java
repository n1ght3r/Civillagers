package com.civillagers.ai.guard;

import com.civillagers.entity.guard.Guard;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

/**
 * Closes in on the target and strikes it with whatever the main hand holds, unless that is a bow or a
 * crossbow, which {@link GuardBowGoal} and {@link GuardCrossbowGoal} shoot instead. Vanilla's melee goal
 * searches a new path every few ticks while its target moves at all; this one keeps vanilla's strike and
 * stopping rules but searches through {@link GuardCombatPath}, so only when the target has really moved,
 * and within the per-tick budget.
 *
 * <p>It strikes once every {@link Guard#strikeInterval()} ticks: 20 with a sword, as vanilla's mobs, and
 * slower with a slower weapon. The wait is kept on the guard, not here, so a fight that starts over (a new
 * target, the same one picked again) strikes no sooner; vanilla's goal would strike at once.
 */
public final class GuardMeleeGoal extends MeleeAttackGoal {
    /** What vanilla's goal is built with; unused, as the guard runs at its level's speed ({@link Guard#runSpeed()}). */
    private static final double SPEED = 0.52;
    private final Guard guard;
    private final GuardCombatPath path;

    public GuardMeleeGoal(Guard guard) {
        super(guard, SPEED, true);
        this.guard = guard;
        this.path = new GuardCombatPath(guard);
    }

    /**
     * Whether the guard has a living target to fight hand to hand. Vanilla also searches a path here, once
     * a second; this leaves the search to {@link #tick()}, where the budget applies.
     */
    @Override
    public boolean canUse() {
        LivingEntity target = guard.getTarget();
        return target != null && target.isAlive() && !guard.holdsRangedWeapon();
    }

    @Override
    public boolean canContinueToUse() {
        return !guard.holdsRangedWeapon() && super.canContinueToUse();
    }

    @Override
    public void start() {
        super.start();
        path.reset();
    }

    @Override
    public void tick() {
        LivingEntity target = guard.getTarget();
        if (target == null) return;
        guard.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (!guard.isWithinMeleeAttackRange(target)) path.follow(target, guard.runSpeed());
        checkAndPerformAttack(target);
    }

    // Vanilla keeps its strike cooldown private, counts it down in its own tick, which this replaces, and
    // clears it when the goal starts; the guard keeps its own instead.

    @Override
    protected boolean isTimeToAttack() {
        return guard.mayStrike();
    }

    @Override
    protected void resetAttackCooldown() {
        guard.struck();
    }

    @Override
    protected int getTicksUntilNextAttack() {
        return guard.ticksUntilStrike();
    }

    @Override
    protected int getAttackInterval() {
        return guard.strikeInterval();
    }
}
