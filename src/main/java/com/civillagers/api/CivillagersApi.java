package com.civillagers.api;

import com.civillagers.entity.CivVillager;
import com.civillagers.entity.ZombieVillagers;
import net.minecraft.world.entity.EntityType;

import java.util.function.Supplier;

/** Registrations an addon makes from its initializer. */
public final class CivillagersApi {
    private CivillagersApi() { }

    /**
     * Makes a villager of the addon's own, of {@code type}, turn into a zombie villager when a zombie kills
     * it (with a villager's odds) and come back as {@code type} when cured, keeping its gear and clothes.
     * The guard is registered by the mod. {@code type} is read only when it is needed, so pass the supplier
     * the addon registers its entity type with.
     */
    public static void turnsIntoZombie(Supplier<? extends EntityType<? extends CivVillager>> type) {
        ZombieVillagers.register(type);
    }
}
