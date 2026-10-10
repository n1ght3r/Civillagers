package com.civillagers.dev.fabric;

import com.civillagers.dev.CivillagersDev;
import net.fabricmc.api.ModInitializer;

/** The dev tools' Fabric entrypoint. Everything it starts is loader independent. */
public final class CivillagersDevFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        CivillagersDev.initialize();
    }
}
