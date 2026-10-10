package com.civillagers.dev.fabric;

import com.civillagers.dev.agent.client.CivillagersDevClient;
import net.fabricmc.api.ClientModInitializer;

/** The dev tools' Fabric client entrypoint: the agent, polled on the client tick. */
public final class CivillagersDevFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        CivillagersDevClient.initialize();
    }
}
