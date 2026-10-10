package com.civillagers.client.fabric;

import com.civillagers.client.CivillagersClient;
import net.fabricmc.api.ClientModInitializer;

/** The Fabric {@code client} entrypoint. Everything it starts is loader independent. */
public final class CivillagersFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        CivillagersClient.initialize();
    }
}
