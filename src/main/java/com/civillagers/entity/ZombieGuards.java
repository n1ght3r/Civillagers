package com.civillagers.entity;

import com.civillagers.platform.Vanilla;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.level.block.LevelEvent;

/**
 * A guard a zombie kills rises as a zombie villager, as a villager does, and comes back a guard when it
 * is cured. The zombie is vanilla's own zombie villager, so it looks, fights and is cured exactly like
 * one (a splash of weakness, then a golden apple); it keeps the guard's gear and the clothes of its
 * villager type, and carries {@link #TAG}, which vanilla saves with it, to say it was a guard.
 *
 * <p>{@code ZombieMixin} calls {@link #turns} and {@link #zombify} when a zombie kills a guard;
 * {@code ZombieVillagerMixin} calls {@link #cure} when a zombie villager's cure finishes.
 */
public final class ZombieGuards {
    /** The entity tag on a zombie villager that was a guard. */
    public static final String TAG = "civillagers.zombie_guard";

    private ZombieGuards() { }

    /** Whether a guard a zombie kills turns, with vanilla's odds for a villager: never on Easy, half the time on Normal, always on Hard. */
    public static boolean turns(Difficulty difficulty, RandomSource random) {
        if (difficulty == Difficulty.HARD) return true;
        return difficulty == Difficulty.NORMAL && random.nextBoolean();
    }

    /**
     * Turns {@code guard} into a zombie villager that keeps its gear, its name and the clothes of its
     * villager type, and never despawns, so the gear a player gave is not lost. Returns it, or null.
     */
    public static ZombieVillager zombify(ServerLevel level, Guard guard) {
        String type = guard.villagerType();
        ZombieVillager zombie = Vanilla.convert(guard, Vanilla.zombieVillagerType(), created -> {
            Vanilla.setVillagerType(created, type);
            created.addTag(TAG);
            created.setPersistenceRequired();
        });
        if (zombie != null && !guard.isSilent()) level.levelEvent(LevelEvent.SOUND_ZOMBIE_INFECTED, zombie.blockPosition(), 0);
        return zombie;
    }

    /**
     * Turns a cured zombie villager that was a guard back into a guard, with its gear, its name and its
     * villager type. Returns the guard, or null if the zombie was never a guard (vanilla then makes it a
     * villager) or no guard could be made.
     */
    public static Guard cure(ServerLevel level, ZombieVillager zombie) {
        if (!zombie.entityTags().contains(TAG)) return null;
        String type = Vanilla.villagerTypeOf(zombie, Guard.DEFAULT_VILLAGER_TYPE);
        Guard guard = Vanilla.convert(zombie, CivEntities.guard(), created -> {
            created.setVillagerType(type);
            created.removeTag(TAG);
        });
        if (guard != null && !zombie.isSilent()) level.levelEvent(LevelEvent.SOUND_ZOMBIE_CONVERTED, guard.blockPosition(), 0);
        return guard;
    }
}
