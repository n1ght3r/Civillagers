package com.civillagers.ai;

import com.civillagers.entity.CivVillager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * A villager of the mod that does not fight goes home and stays there while the bell it heard is still
 * ringing in its ears ({@link CivVillager#alarmBell()}) or a raid is being fought around it, as villagers
 * hide. Home is the nearest bed within {@link #SEARCH_RADIUS}, claimed or not: beds are where the houses
 * are. With none near, it stays where it is.
 *
 * <p>The bed is looked up once per alarm and again at most every {@link #SEARCH_INTERVAL} ticks, and
 * walking there is a path from {@link GuardPathBudget}, searched again only when the last one ended short.
 */
public final class CivHideGoal extends Goal {
    static final int SEARCH_INTERVAL = 40;
    static final int SEARCH_RADIUS = 32;
    private static final double SPEED = 0.7;
    /** Close enough to the bed to count as indoors, horizontally. */
    private static final double HOME_SQR = 3.0 * 3.0;
    private static final int REPATH = 40;

    private final CivVillager villager;
    private BlockPos home;
    private int nextSearch;
    private int repath;

    public CivHideGoal(CivVillager villager) {
        this.villager = villager;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    /** Whether {@code villager} has a reason to hide: a civilian, with the bell ringing or a raid around it. */
    public static boolean wantsToHide(CivVillager villager) {
        return !villager.isCombatant() && (villager.alarmBell() != null || villager.raidCenter() != null);
    }

    @Override
    public boolean canUse() {
        if (villager.tickCount < nextSearch || villager.isSleeping() || !wantsToHide(villager)) return false;
        nextSearch = villager.tickCount + SEARCH_INTERVAL;
        ServerLevel level = villager.serverLevel();
        if (level == null) return false;
        home = Homes.nearestBed(level, villager.blockPosition(), SEARCH_RADIUS, false);
        return home != null;
    }

    @Override
    public boolean canContinueToUse() {
        return home != null && wantsToHide(villager);
    }

    @Override
    public void start() {
        repath = 0;
    }

    @Override
    public void stop() {
        home = null;
        villager.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (home == null) return;
        double dx = home.getX() + 0.5 - villager.getX();
        double dz = home.getZ() + 0.5 - villager.getZ();
        if (dx * dx + dz * dz <= HOME_SQR) {
            villager.getNavigation().stop();
            return;
        }
        // Ticked every other server tick, so each call stands for two.
        repath -= 2;
        if (!villager.getNavigation().isDone() || repath > 0) return;
        ServerLevel level = villager.serverLevel();
        if (level == null || !GuardPathBudget.tryTake(level.getGameTime())) return;
        repath = REPATH;
        villager.getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, SPEED);
    }
}
