package com.civillagers.dev.platform;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;

import java.util.List;

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

    /** A crossbow loaded with one arrow. After 1.21.1, loaded projectiles are built from a list by another name. */
    public static ItemStack loadedCrossbow() {
        ItemStack crossbow = new ItemStack(Items.CROSSBOW);
        List<ItemStack> arrow = List.of(new ItemStack(Items.ARROW));
        //? if >1.21.1 {
        crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.ofNonEmpty(arrow));
        //?} else
        //crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(arrow));
        return crossbow;
    }

    /** The iron golem's entity type; see {@link #zombie()}. */
    public static EntityType<? extends Mob> ironGolem() {
        //? if >=26.2 {
        return net.minecraft.world.entity.EntityTypes.IRON_GOLEM;
        //?} else
        //return EntityType.IRON_GOLEM;
    }
}
