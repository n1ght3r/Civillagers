package com.civillagers.api;

import com.civillagers.entity.CivVillager;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;

/** Hooks every villager of the mod fires: guards today, the chief and builders later, and an addon's own. */
public final class CivVillagerEvents {
    private CivVillagerEvents() { }

    /**
     * A villager registers its goals, once, server side, from its constructor, after the mod's own: add an
     * addon's goals here, or remove the mod's. The villager's own fields are not set yet.
     */
    public static final Listeners<RegisterGoals> REGISTER_GOALS = new Listeners<>();

    /** A zombie killed a villager and it rose as {@code zombie}, which keeps its gear. The villager is already removed. */
    public static final Listeners<TurnedZombie> TURNED_ZOMBIE = new Listeners<>();

    /** A zombie villager that was a villager of the mod was cured back into {@code cured}. The zombie is already removed. */
    public static final Listeners<Cured> CURED = new Listeners<>();

    @FunctionalInterface
    public interface RegisterGoals {
        void registerGoals(CivVillager villager, GoalSelector goals, GoalSelector targets);
    }

    @FunctionalInterface
    public interface TurnedZombie {
        void turnedZombie(CivVillager villager, ZombieVillager zombie);
    }

    @FunctionalInterface
    public interface Cured {
        void cured(ZombieVillager zombie, CivVillager cured);
    }
}
