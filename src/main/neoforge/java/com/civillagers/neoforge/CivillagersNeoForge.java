package com.civillagers.neoforge;

import com.civillagers.Civillagers;
import net.neoforged.fml.common.Mod;

/** The NeoForge mod class, the counterpart of the Fabric {@code main} entrypoint. */
@Mod(Civillagers.MOD_ID)
public final class CivillagersNeoForge {
    public CivillagersNeoForge() {
        Civillagers.initialize();
    }
}
