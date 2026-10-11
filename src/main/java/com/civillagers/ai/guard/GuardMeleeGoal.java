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
 */
public final class GuardMeleeGoal extends MeleeAttackGoal {
    /** Faster than a patrol: a guard runs to a fight. */
    static final double SPEED = 0.75;
    /** Ticks between two strikes, as vanilla's melee goal has it. */
    private static final int ATTACK_INTERVAL = 20;

    private final Guard guard;
    private final GuardCombatPath path;
    private int attackCooldown;

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
        attackCooldown = 0;
    }

    @Override
    public void tick() {
        LivingEntity target = guard.getTarget();
        if (target == null) return;
        guard.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (!guard.isWithinMeleeAttackRange(target)) path.follow(target, SPEED);
        if (attackCooldown > 0) attackCooldown--;
        checkAndPerformAttack(target);
    }

    // Vanilla keeps its strike cooldown private and counts it down in its own tick, which this replaces.

    @Override
    protected boolean isTimeToAttack() {
        return attackCooldown <= 0;
    }

    @Override
    protected void resetAttackCooldown() {
        attackCooldown = ATTACK_INTERVAL;
    }

    @Override
    protected int getTicksUntilNextAttack() {
        return attackCooldown;
    }

    @Override
    protected int getAttackInterval() {
        return ATTACK_INTERVAL;
    }
}
