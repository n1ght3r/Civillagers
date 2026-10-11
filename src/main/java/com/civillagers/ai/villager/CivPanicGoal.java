package com.civillagers.ai.villager;

import com.civillagers.ai.GuardPathBudget;
import com.civillagers.entity.CivVillager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;
import java.util.function.Predicate;

/**
 * A villager of the mod that does not fight ({@link CivVillager#isCombatant()} false) runs, as a villager
 * does: from whatever just hurt it, and from a zombie or a raider that comes too close, each at vanilla's
 * distance for a villager ({@link #fleeDistance}). It runs to a point away from the danger and looks again
 * when it gets there.
 *
 * <p>It looks for danger at most once every {@link #CHECK_INTERVAL} ticks, in one bounded box query, and a
 * run is one path search from the fight's budget in {@link GuardPathBudget}.
 */
public final class CivPanicGoal extends Goal {
    /** Ticks between two looks for danger. */
    public static final int CHECK_INTERVAL = 10;
    /** The farthest anything is fled from, in blocks: a pillager's. */
    private static final double WATCH = 15.0;
    private static final double SPEED = 0.8;

    /** The first filter on a look for danger; no capture, so no allocation. */
    private static final Predicate<Mob> DANGEROUS = mob -> mob.isAlive() && fleeDistance(mob) > 0;

    private final CivVillager villager;
    private int nextCheck;

    public CivPanicGoal(CivVillager villager) {
        this.villager = villager;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    /**
     * How close {@code mob} may come before a villager runs from it, in blocks, or 0 if it does not: zombies
     * (not zombified piglins) and vexes 8, zoglins 10, raiders 12 (not witches), close to vanilla's figures
     * for a villager, which give each raider its own.
     */
    public static double fleeDistance(Mob mob) {
        if (mob instanceof Zombie) return mob instanceof ZombifiedPiglin ? 0 : 8;
        if (mob instanceof Vex) return 8;
        if (mob instanceof Zoglin) return 10;
        if (mob instanceof Raider) return mob instanceof Witch ? 0 : 12;
        return 0;
    }

    @Override
    public boolean canUse() {
        // The tick count first: it is the cheapest, and what keeps the goal from asking every tick.
        if (villager.tickCount < nextCheck || villager.isCombatant() || villager.isSleeping()) return false;
        nextCheck = villager.tickCount + CHECK_INTERVAL;
        ServerLevel level = villager.serverLevel();
        if (level == null) return false;
        LivingEntity danger = villager.getLastHurtByMob();
        if (danger == null) danger = nearestDanger(level);
        if (danger == null) return false;
        Vec3 away = DefaultRandomPos.getPosAway(villager, 16, 7, danger.position());
        if (away == null || !GuardPathBudget.tryTakeCombat(level.getGameTime())) return false;
        return villager.getNavigation().moveTo(away.x, away.y, away.z, SPEED);
    }

    @Override
    public boolean canContinueToUse() {
        return !villager.getNavigation().isDone() && !villager.isCombatant();
    }

    @Override
    public void stop() {
        // Look again at once: the danger may have followed.
        nextCheck = villager.tickCount;
    }

    private Mob nearestDanger(ServerLevel level) {
        AABB box = villager.getBoundingBox().inflate(WATCH, 4.0, WATCH);
        List<Mob> mobs = level.getEntitiesOfClass(Mob.class, box, DANGEROUS);
        Mob nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (int i = 0; i < mobs.size(); i++) {
            Mob mob = mobs.get(i);
            double distance = villager.distanceToSqr(mob);
            double flee = fleeDistance(mob);
            if (distance > flee * flee || distance >= nearestDistance) continue;
            nearest = mob;
            nearestDistance = distance;
        }
        return nearest != null && villager.getSensing().hasLineOfSight(nearest) ? nearest : null;
    }
}
