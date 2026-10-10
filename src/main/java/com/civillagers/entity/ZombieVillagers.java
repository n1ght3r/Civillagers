package com.civillagers.entity;

import com.civillagers.api.CivVillagerEvents;
import com.civillagers.platform.Vanilla;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.level.block.LevelEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * A {@link CivVillager} a zombie kills rises as a zombie villager, as a villager does, and comes back as
 * what it was when cured. Only the types {@linkplain #register registered} here turn: the guard, and an
 * addon's own through {@link com.civillagers.api.CivillagersApi#turnsIntoZombie}.
 *
 * <p>The zombie is vanilla's own zombie villager, so it looks, fights and is cured exactly like one (a
 * splash of weakness, then a golden apple); it keeps the villager's gear, name and the clothes of its
 * villager type, and carries the entity tag {@link #tagFor} names, which vanilla saves with it, to say what
 * it is cured into: {@code civillagers.zombie_of.civillagers.guard} for a guard.
 *
 * <p>{@code ZombieMixin} calls {@link #turns} and {@link #zombify} when a zombie kills a villager of the
 * mod; {@code ZombieVillagerMixin} calls {@link #cure} when a zombie villager's cure finishes.
 */
public final class ZombieVillagers {
    /** What an entity tag on a zombie villager starts with when it is cured into a villager of the mod. */
    public static final String TAG_PREFIX = "civillagers.zombie_of.";

    /** The types that turn and are cured back. Registered while the game loads, read on the server thread. */
    private static final List<Supplier<? extends EntityType<? extends CivVillager>>> TYPES = new ArrayList<>();

    private ZombieVillagers() { }

    /** Makes villagers of {@code type} turn into zombie villagers and be cured back. */
    public static synchronized void register(Supplier<? extends EntityType<? extends CivVillager>> type) {
        TYPES.add(type);
    }

    /** The entity tag a zombie villager cured into {@code type} carries: the prefix, then the type's namespace and path. */
    public static String tagFor(EntityType<?> type) {
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return TAG_PREFIX + id.getNamespace() + "." + id.getPath();
    }

    /** Whether a villager a zombie kills turns, with vanilla's odds for a villager: never on Easy, half the time on Normal, always on Hard. */
    public static boolean turns(Difficulty difficulty, RandomSource random) {
        if (difficulty == Difficulty.HARD) return true;
        return difficulty == Difficulty.NORMAL && random.nextBoolean();
    }

    /** Whether villagers of {@code type} turn into zombie villagers. */
    public static boolean turnsIntoZombie(EntityType<?> type) {
        return registered(type) != null;
    }

    /**
     * Turns {@code villager} into a zombie villager that keeps its gear, its name and the clothes of its
     * villager type, and never despawns, so gear a player gave is not lost. Returns it, or null if
     * villagers of its type do not turn or no zombie could be made.
     */
    public static ZombieVillager zombify(ServerLevel level, CivVillager villager) {
        if (!turnsIntoZombie(villager.getType())) return null;
        String type = villager.villagerType();
        String tag = tagFor(villager.getType());
        ZombieVillager zombie = Vanilla.convert(villager, Vanilla.zombieVillagerType(), created -> {
            Vanilla.setVillagerType(created, type);
            created.addTag(tag);
            created.setPersistenceRequired();
        });
        if (zombie == null) return null;
        if (!villager.isSilent()) level.levelEvent(LevelEvent.SOUND_ZOMBIE_INFECTED, zombie.blockPosition(), 0);
        for (int i = 0; i < CivVillagerEvents.TURNED_ZOMBIE.size(); i++) CivVillagerEvents.TURNED_ZOMBIE.get(i).turnedZombie(villager, zombie);
        return zombie;
    }

    /**
     * Turns a cured zombie villager back into the villager of the mod it was, with its gear, its name and
     * its villager type. Returns it, or null if the zombie was never one (vanilla then makes it a villager)
     * or nothing could be made.
     */
    public static CivVillager cure(ServerLevel level, ZombieVillager zombie) {
        EntityType<? extends CivVillager> type = curesInto(zombie);
        if (type == null) return null;
        String tag = tagFor(type);
        String villagerType = Vanilla.villagerTypeOf(zombie, CivVillager.DEFAULT_VILLAGER_TYPE);
        CivVillager cured = Vanilla.convert(zombie, type, created -> {
            created.setVillagerType(villagerType);
            created.removeTag(tag);
        });
        if (cured == null) return null;
        if (!zombie.isSilent()) level.levelEvent(LevelEvent.SOUND_ZOMBIE_CONVERTED, cured.blockPosition(), 0);
        for (int i = 0; i < CivVillagerEvents.CURED.size(); i++) CivVillagerEvents.CURED.get(i).cured(zombie, cured);
        return cured;
    }

    /** The registered type {@code zombie}'s tag says it is cured into, or null for vanilla's villager. */
    public static EntityType<? extends CivVillager> curesInto(ZombieVillager zombie) {
        for (String tag : zombie.entityTags()) {
            if (!tag.startsWith(TAG_PREFIX)) continue;
            for (int i = 0; i < TYPES.size(); i++) {
                EntityType<? extends CivVillager> type = TYPES.get(i).get();
                if (tag.equals(tagFor(type))) return type;
            }
        }
        return null;
    }

    private static EntityType<? extends CivVillager> registered(EntityType<?> type) {
        for (int i = 0; i < TYPES.size(); i++) {
            EntityType<? extends CivVillager> candidate = TYPES.get(i).get();
            if (candidate == type) return candidate;
        }
        return null;
    }
}
