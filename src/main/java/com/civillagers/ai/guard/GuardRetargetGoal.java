package com.civillagers.ai.guard;

import com.civillagers.entity.guard.Guard;
import com.civillagers.entity.guard.GuardStats;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.AbstractVillager;

/**
 * While a guard fights, looks again now and then for a nearer mob to fight: the nearest threat it sees
 * ({@link Guard#findThreat()}), and the mob that hurt it last. One the guard
 * {@linkplain Guard#prefers prefers}, clearly nearer than its target, becomes the target. Whatever set the
 * target (a hostile mob seen, a blow, a raid) a guard does not walk past a vindicator hitting it to reach
 * an archer far behind.
 *
 * <p>Never too fast: it looks at most every {@link #INTERVAL} ticks, and only once
 * {@link Guard#RETARGET_COOLDOWN} ticks have gone by since the target last changed, so a guard never
 * flicks between two mobs. A look is the target scan's own box query, only while the guard fights.
 *
 * <p>The goal takes no flags and never runs: it does its work in {@link #canUse()}, beside whichever
 * target goal holds the target, and that goal goes on with the new one.
 */
public final class GuardRetargetGoal extends Goal {
    /** The fewest ticks between two looks for a nearer mob. */
    public static final int INTERVAL = 20;
    /** How recent a blow, in ticks, for the mob that struck it to count. */
    private static final int RECENT_HURT = 60;

    private final Guard guard;
    private int nextLook;

    public GuardRetargetGoal(Guard guard) {
        this.guard = guard;
    }

    @Override
    public boolean canUse() {
        // Cheap reads first: most guards are not fighting, and those that are look once a second at most.
        LivingEntity target = guard.getTarget();
        if (target == null || guard.tickCount < nextLook || !guard.isCombatant()) return false;
        nextLook = guard.tickCount + INTERVAL;
        if (!guard.mayRetarget()) return false;
        retarget(target);
        return false;
    }

    /** Looks once for a nearer mob than {@code target}, and makes it the target if the guard prefers it. Public for the gametests. */
    public void retarget(LivingEntity target) {
        LivingEntity best = null;
        LivingEntity attacker = guard.getLastHurtByMob();
        if (attacker instanceof Mob && attacker != target && guard.tickCount - guard.getLastHurtByMobTimestamp() <= RECENT_HURT
                && !(attacker instanceof Guard) && !(attacker instanceof AbstractVillager) && !(attacker instanceof IronGolem)
                && guard.canAttack(attacker)) {
            best = attacker;
        }
        GuardStats.recordScan();
        LivingEntity seen = guard.findThreat();
        if (seen != null && seen != target && (best == null || guard.targetScore(seen) < guard.targetScore(best))) best = seen;
        if (guard.prefers(best, target)) guard.setTarget(best);
    }
}
