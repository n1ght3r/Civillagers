package com.civillagers.dev.neoforge;

import com.civillagers.dev.CivillagersDev;
import com.civillagers.dev.agent.client.CivillagersDevClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

/** The dev tools' NeoForge client mod class: the agent, polled on the client tick. */
@Mod(value = CivillagersDev.NEOFORGE_MOD_ID, dist = Dist.CLIENT)
public final class CivillagersDevNeoForgeClient {
    public CivillagersDevNeoForgeClient() {
        CivillagersDevClient.initialize();
    }
}
