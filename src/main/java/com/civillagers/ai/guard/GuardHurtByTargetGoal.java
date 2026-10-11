package com.civillagers.ai.guard;

import com.civillagers.entity.guard.Guard;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;

/**
 * Turns a guard on what hurts it, and calls the guards near it, as vanilla's goal does, but never pulls it
 * off a fight for any blow at all. With a target already, it turns only on an attacker the guard
 * {@linkplain Guard#prefers prefers} (clearly nearer), and not within {@link Guard#RETARGET_COOLDOWN}
 * ticks of its last change. Vanilla's goal turns on the last mob to land a blow: a guard fighting a
 * vindicator beside it while a pillager shot at it from afar turned to the pillager with every arrow, and
 * spent the fight walking towards it.
 */
public final class GuardHurtByTargetGoal extends HurtByTargetGoal {
    private final Guard guard;

    public GuardHurtByTargetGoal(Guard guard, Class<?>... ignoreDamageFrom) {
        super(guard, ignoreDamageFrom);
        this.guard = guard;
    }

    @Override
    public boolean canUse() {
        LivingEntity attacker = guard.getLastHurtByMob();
        if (attacker == null || guard.gaveUpOn(attacker)) return false;
        LivingEntity current = guard.getTarget();
        if (current != null && current.isAlive() && current != attacker
                && !(guard.mayRetarget() && guard.prefers(attacker, current))) {
            return false;
        }
        return super.canUse();
    }
}
