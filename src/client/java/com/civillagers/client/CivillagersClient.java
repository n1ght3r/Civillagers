package com.civillagers.client;

import com.civillagers.client.platform.ClientLoader;
import com.civillagers.client.platform.CivVillagerRenderer;
import com.civillagers.entity.CivEntities;

public final class CivillagersClient {
    private CivillagersClient() { }

    /**
     * Called once by the loader's client entrypoint. Entity renderers, models and screens are registered
     * from here, through {@code client/platform/ClientLoader}.
     */
    public static void initialize() {
        CivVillagerRenderer.registerLayers();
        ClientLoader.registerEntityRenderer(CivEntities::guard, CivVillagerRenderer::new);
    }
}
