package com.civillagers.ai.guard;

import com.civillagers.entity.guard.Guard;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;

/**
 * Shoots the target with the crossbow in the main hand, the way a pillager does: closes to within range,
 * charges, waits a moment with the bolt loaded, looses, and charges again. Vanilla's
 * {@code RangedCrossbowAttackGoal} only takes monsters, so this is its logic for a guard, with the path
 * searches going through {@link GuardCombatPath}. Charging is the guard using the crossbow, and a loaded
 * crossbow carries its bolt, both of which reach the client, so the renderer poses the arms from them.
 * A guard needs no arrows ({@link Guard#getProjectile}).
 */
public final class GuardCrossbowGoal extends Goal {
    private static final double SPEED = 0.6;
    /** Closer than a bow's: a crossbow bolt flies flatter but the guard waits to see its target first. */
    private static final float RANGE = 12.0F;
    private static final double RANGE_SQR = RANGE * RANGE;
    /** Ticks of sight before the guard stands and charges. */
    private static final int SIGHT_BEFORE_CHARGING = 5;
    /**
     * Ticks a loaded crossbow is held before it is shot: at least the first, plus up to the second. Shorter
     * than a pillager's wait, so a guard shoots about as often with a crossbow as with a bow.
     */
    private static final int HOLD = 5;
    private static final int HOLD_JITTER = 10;
    /** As fast as a pillager's bolt. */
    private static final float VELOCITY = 1.6F;

    private enum State { UNCHARGED, CHARGING, CHARGED, READY }

    private final Guard guard;
    private final GuardCombatPath path;
    private State state = State.UNCHARGED;
    private int seeTime;
    private int holdTime;

    public GuardCrossbowGoal(Guard guard) {
        this.guard = guard;
        this.path = new GuardCombatPath(guard);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = guard.getTarget();
        return target != null && target.isAlive() && guard.holdsCrossbow();
    }

    @Override
    public void start() {
        guard.setAggressive(true);
        path.reset();
        // A crossbow handed over loaded is ready to shoot.
        state = CrossbowItem.isCharged(guard.getMainHandItem()) ? State.CHARGED : State.UNCHARGED;
        holdTime = HOLD;
    }

    @Override
    public void stop() {
        guard.setAggressive(false);
        seeTime = 0;
        // A charge that was not finished is lost, as it is for a pillager; a loaded crossbow stays loaded.
        if (guard.isUsingItem() && guard.getUsedItemHand() == InteractionHand.MAIN_HAND) guard.stopUsingItem();
        if (state == State.CHARGING) state = State.UNCHARGED;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = guard.getTarget();
        if (target == null) return;
        boolean seen = guard.getSensing().hasLineOfSight(target);
        if (seen != seeTime > 0) seeTime = 0;
        seeTime += seen ? 1 : -1;

        boolean needsToMove = (guard.distanceToSqr(target) > RANGE_SQR || seeTime < SIGHT_BEFORE_CHARGING)
                && state != State.CHARGED;
        if (needsToMove) {
            path.follow(target, state == State.UNCHARGED ? SPEED : SPEED * 0.5);
        } else {
            guard.getNavigation().stop();
        }
        guard.getLookControl().setLookAt(target, 30.0F, 30.0F);

        ItemStack crossbow = guard.getMainHandItem();
        switch (state) {
            case UNCHARGED -> {
                if (!needsToMove) {
                    guard.startUsingItem(InteractionHand.MAIN_HAND);
                    state = State.CHARGING;
                }
            }
            case CHARGING -> {
                if (!guard.isUsingItem()) {
                    state = State.UNCHARGED;
                } else if (guard.getTicksUsingItem() >= CrossbowItem.getChargeDuration(crossbow, guard)) {
                    // Releasing at full charge loads the bolt.
                    guard.releaseUsingItem();
                    state = State.CHARGED;
                    holdTime = HOLD + guard.getRandom().nextInt(HOLD_JITTER);
                }
            }
            case CHARGED -> {
                if (--holdTime <= 0) state = State.READY;
            }
            case READY -> {
                if (seen) {
                    shoot(target, crossbow);
                    state = State.UNCHARGED;
                }
            }
        }
    }

    private void shoot(LivingEntity target, ItemStack crossbow) {
        if (crossbow.getItem() instanceof CrossbowItem item) {
            float inaccuracy = 14 - guard.level().getDifficulty().getId() * 4;
            item.performShooting(guard.level(), guard, InteractionHand.MAIN_HAND, crossbow, VELOCITY, inaccuracy, target);
        }
    }
}
