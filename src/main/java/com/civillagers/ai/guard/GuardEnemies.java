package com.civillagers.ai.guard;

import com.civillagers.entity.guard.Guard;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.entity.raid.Raider;

/**
 * The vanilla mobs that go after guards the way they go after villagers and iron golems: zombies, and
 * the raiders that fight (illagers and ravagers). Each gets one more target goal beside its own, ahead of
 * its villager and golem targets ({@link #PRIORITY}), and seeing the guard first. Zombified piglins
 * are left out, being neutral, and drowned, which only hunt from the water; witches, which only throw
 * potions at players, too.
 *
 * <p>Called from {@code MobMixin} once a mob has registered its own goals, on the server only.
 */
public final class GuardEnemies {
    /**
     * Before vanilla's villager and iron golem targets (3) and beside the player's (2): a guard in sight is
     * the one these mobs go for, drawing them off the villagers it protects.
     */
    public static final int PRIORITY = 2;

    private GuardEnemies() { }

    /** Adds the guard target to {@code mob}'s target goals if it is one of the mobs above. */
    public static void addTargetGoal(Mob mob, GoalSelector targets) {
        if (huntsGuards(mob)) targets.addGoal(PRIORITY, new NearestAttackableTargetGoal<>(mob, Guard.class, true));
    }

    /** Whether {@code mob} goes after guards. */
    public static boolean huntsGuards(Mob mob) {
        if (mob instanceof Zombie) return !(mob instanceof ZombifiedPiglin) && !(mob instanceof Drowned);
        return mob instanceof Raider && !(mob instanceof Witch);
    }
}
