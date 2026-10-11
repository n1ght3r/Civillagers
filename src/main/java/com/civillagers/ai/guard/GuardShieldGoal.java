package com.civillagers.ai.guard;

import com.civillagers.entity.guard.Guard;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Raises the shield in the guard's off hand against what a sword cannot answer in time: a target that
 * shoots, while the guard is still closing in on it, and a creeper about to blow up beside it. The shield
 * comes down once the archer is within reach, so the guard can strike, and once the creeper has gone off
 * or calmed down. A guard with a bow or a crossbow keeps its hands for that.
 *
 * <p>The goal takes no flags, so it runs beside the fight rather than instead of it. A ranged target is
 * read from what the guard already knows; a creeper takes one small box query, at most once every
 * {@link #CREEPER_CHECK_INTERVAL} ticks, and only for a guard that holds a shield.
 */
public final class GuardShieldGoal extends Goal {
    /** Ticks between two looks for a creeper about to blow up. A creeper's fuse is 30 ticks. */
    public static final int CREEPER_CHECK_INTERVAL = 5;
    /** How close a creeper about to blow up has to be, in blocks, to raise the shield against it. */
    public static final double CREEPER_RANGE = 5.0;

    private static final EntityTypeTest<net.minecraft.world.entity.Entity, Creeper> CREEPERS = EntityTypeTest.forClass(Creeper.class);
    private static final Predicate<Creeper> ABOUT_TO_BLOW = creeper -> creeper.isAlive() && (creeper.getSwellDir() > 0 || creeper.isIgnited());

    private final Guard guard;
    /** Reused by every creeper query, so a query allocates no list. */
    private final List<Creeper> found = new ArrayList<>(1);
    private Creeper creeper;
    private int nextCreeperCheck;

    public GuardShieldGoal(Guard guard) {
        this.guard = guard;
    }

    @Override
    public boolean canUse() {
        return guard.holdsShield() && !guard.holdsRangedWeapon() && inDanger();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse() && guard.isUsingItem() && guard.getUsedItemHand() == InteractionHand.OFF_HAND;
    }

    @Override
    public void start() {
        guard.startUsingItem(InteractionHand.OFF_HAND);
    }

    @Override
    public void stop() {
        if (guard.isUsingItem() && guard.getUsedItemHand() == InteractionHand.OFF_HAND) guard.stopUsingItem();
        creeper = null;
        found.clear();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    /** Keeps the shield towards the creeper: a shield only stops what comes at its front. */
    @Override
    public void tick() {
        if (creeper != null) guard.getLookControl().setLookAt(creeper, 30.0F, 30.0F);
    }

    /** Whether something calls for the shield now. */
    private boolean inDanger() {
        LivingEntity target = guard.getTarget();
        if (target != null && target.isAlive() && shoots(target) && !guard.isWithinMeleeAttackRange(target)) return true;
        return creeperAboutToBlow() != null;
    }

    /** Whether {@code mob} attacks from afar: a mob made to, or one holding a bow or a crossbow. */
    public static boolean shoots(LivingEntity mob) {
        return mob instanceof RangedAttackMob || mob.getMainHandItem().getItem() instanceof ProjectileWeaponItem;
    }

    /** The creeper about to blow up within {@link #CREEPER_RANGE}, as of the last look, or null. */
    private Creeper creeperAboutToBlow() {
        if (creeper != null && !ABOUT_TO_BLOW.test(creeper)) creeper = null;
        if (guard.tickCount < nextCreeperCheck) return creeper;
        nextCreeperCheck = guard.tickCount + CREEPER_CHECK_INTERVAL;
        AABB box = guard.getBoundingBox().inflate(CREEPER_RANGE);
        guard.level().getEntities(CREEPERS, box, ABOUT_TO_BLOW, found, 1);
        creeper = found.isEmpty() ? null : found.get(0);
        found.clear();
        return creeper;
    }
}
