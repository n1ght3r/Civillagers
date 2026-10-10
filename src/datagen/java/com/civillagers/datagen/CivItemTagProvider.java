package com.civillagers.datagen;

import com.civillagers.api.CivTags;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;

import java.util.concurrent.CompletableFuture;

/**
 * The mod's item tags, which datapacks and other mods add to ({@link CivTags}).
 *
 * <p>{@code guard_weapons}: what a guard takes in its main hand. Vanilla's sword and axe tags rather than
 * each item, so a mod's sword that joins {@code minecraft:swords} is a guard's weapon too.
 */
public final class CivItemTagProvider extends FabricTagsProvider<Item> {
    public CivItemTagProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, Registries.ITEM, registries);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        // Fabric API's tag builder was called getOrCreateTagBuilder on 1.21.1.
        //? if >1.21.1 {
        builder(CivTags.GUARD_WEAPONS)
        //?} else
        //getOrCreateTagBuilder(CivTags.GUARD_WEAPONS)
                // Vanilla's tags come from another provider, which this one cannot see, so they are added
                // without the check that they exist.
                .forceAddTag(ItemTags.SWORDS)
                .forceAddTag(ItemTags.AXES)
                .add(vanilla("bow"))
                .add(vanilla("crossbow"));
    }

    private static ResourceKey<Item> vanilla(String path) {
        return ResourceKey.create(Registries.ITEM, Identifier.withDefaultNamespace(path));
    }

    @Override
    public String getName() {
        return "Civillagers Item Tags";
    }
}
