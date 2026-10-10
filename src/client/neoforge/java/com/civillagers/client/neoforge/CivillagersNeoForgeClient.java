package com.civillagers.client.neoforge;

import com.civillagers.Civillagers;
import com.civillagers.client.CivillagersClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

/** The NeoForge client mod class, the counterpart of the Fabric {@code client} entrypoint. */
@Mod(value = Civillagers.MOD_ID, dist = Dist.CLIENT)
public final class CivillagersNeoForgeClient {
    public CivillagersNeoForgeClient() {
        CivillagersClient.initialize();
    }
}
