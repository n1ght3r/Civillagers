package com.civillagers.fabric;

import com.civillagers.Civillagers;
import net.fabricmc.api.ModInitializer;

/** The Fabric {@code main} entrypoint. Everything it starts is loader independent. */
public final class CivillagersFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        Civillagers.initialize();
    }
}
