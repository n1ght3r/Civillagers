package com.civillagers.api;

import com.civillagers.Civillagers;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/** Tags the mod reads, which a datapack or another mod adds to. */
public final class CivTags {
    private CivTags() { }

    /**
     * {@code civillagers:guard_weapons}: what a guard takes in its main hand. Swords, axes, bows and
     * crossbows by default. A guard shoots a bow or crossbow and swings anything else.
     */
    public static final TagKey<Item> GUARD_WEAPONS = TagKey.create(Registries.ITEM, Civillagers.id("guard_weapons"));
}
