package com.civillagers.datagen;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

/**
 * Writes every datapack and asset JSON the mod ships that is not hand-written, into
 * {@code src/main/generated/<minecraft version>}. Providers are added to the pack below. See
 * {@code src/datagen/java/AGENTS.md}.
 */
public final class CivillagersDatagen implements DataGeneratorEntrypoint {
    @Override
    public void onInitializeDataGenerator(FabricDataGenerator generator) {
        FabricDataGenerator.Pack pack = generator.createPack();
        // pack.addProvider(CivillagersRecipeProvider::new); and so on, one provider per kind of file.
    }
}
