package com.civillagers.ai;

import com.civillagers.entity.Guard;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.EnumSet;

/**
 * Shoots the target with the bow in the main hand: keeps within range, draws, looses, waits, the way a
 * skeleton does. Vanilla's {@code RangedBowAttackGoal} only takes monsters, so this is its logic for a
 * guard. A guard needs no arrows; a mob's arrows cannot be picked up.
 */
public final class GuardBowGoal extends Goal {
    private static final double SPEED = 0.6;
    private static final float RANGE = 15.0F;
    private static final double RANGE_SQR = RANGE * RANGE;
    /** Ticks between the end of one shot and the next draw. */
    private static final int COOLDOWN = 20;
    /** Ticks of drawing before a shot: a full draw. */
    private static final int DRAW = 20;
    /** Ticks between two path requests while the target is out of range or out of sight. */
    private static final int REPATH = 10;

    private final Guard guard;
    private int attackTime = -1;
    private int seeTime;
    private int repath;

    public GuardBowGoal(Guard guard) {
        this.guard = guard;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = guard.getTarget();
        return target != null && target.isAlive() && guard.holdsBow();
    }

    @Override
    public void start() {
        guard.setAggressive(true);
    }

    @Override
    public void stop() {
        guard.setAggressive(false);
        seeTime = 0;
        attackTime = -1;
        repath = 0;
        guard.stopUsingItem();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = guard.getTarget();
        if (target == null) return;
        double distance = guard.distanceToSqr(target.getX(), target.getY(), target.getZ());
        boolean seen = guard.getSensing().hasLineOfSight(target);
        if (seen != seeTime > 0) seeTime = 0;
        seeTime += seen ? 1 : -1;

        if (distance <= RANGE_SQR && seeTime >= 20) {
            guard.getNavigation().stop();
        } else if (--repath <= 0) {
            repath = REPATH;
            guard.getNavigation().moveTo(target, SPEED);
        }
        guard.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (guard.isUsingItem()) {
            if (!seen && seeTime < -60) {
                guard.stopUsingItem();
            } else if (seen && guard.getTicksUsingItem() >= DRAW) {
                int drawn = guard.getTicksUsingItem();
                guard.stopUsingItem();
                shoot(target, BowItem.getPowerForTime(drawn));
                attackTime = COOLDOWN;
            }
        } else if (--attackTime <= 0 && seeTime >= -60) {
            guard.startUsingItem(InteractionHand.MAIN_HAND);
        }
    }

    private void shoot(LivingEntity target, float power) {
        ItemStack bow = guard.getMainHandItem();
        AbstractArrow arrow = ProjectileUtil.getMobArrow(guard, new ItemStack(Items.ARROW), power, bow);
        double dx = target.getX() - guard.getX();
        double dy = target.getY(1.0 / 3.0) - arrow.getY();
        double dz = target.getZ() - guard.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + horizontal * 0.2, dz, 1.6F, 14 - guard.level().getDifficulty().getId() * 4);
        guard.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (guard.getRandom().nextFloat() * 0.4F + 0.8F));
        guard.level().addFreshEntity(arrow);
    }
}
