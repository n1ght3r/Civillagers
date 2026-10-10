package com.civillagers.dev.platform;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

/**
 * Where Minecraft differs between versions for the dev tools alone, the same arrangement as the mod's
 * {@code platform/Vanilla}: a Stonecutter block here rather than at a call site.
 */
public final class DevVanilla {
    private DevVanilla() { }

    /** The zombie's entity type. By 26.2 vanilla's entity types moved out of {@code EntityType} into {@code EntityTypes}. */
    public static EntityType<? extends Mob> zombie() {
        //? if >=26.2 {
        return net.minecraft.world.entity.EntityTypes.ZOMBIE;
        //?} else
        //return EntityType.ZOMBIE;
    }

    /** The villager's entity type; see {@link #zombie()}. */
    public static EntityType<? extends Mob> villager() {
        //? if >=26.2 {
        return net.minecraft.world.entity.EntityTypes.VILLAGER;
        //?} else
        //return EntityType.VILLAGER;
    }

    /** The iron golem's entity type; see {@link #zombie()}. */
    public static EntityType<? extends Mob> ironGolem() {
        //? if >=26.2 {
        return net.minecraft.world.entity.EntityTypes.IRON_GOLEM;
        //?} else
        //return EntityType.IRON_GOLEM;
    }
}
