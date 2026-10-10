package com.civillagers.dev.neoforge;

import com.civillagers.dev.CivillagersDev;
import net.neoforged.fml.common.Mod;

/** The dev tools' NeoForge mod class. Everything it starts is loader independent. */
@Mod(CivillagersDev.NEOFORGE_MOD_ID)
public final class CivillagersDevNeoForge {
    public CivillagersDevNeoForge() {
        CivillagersDev.initialize();
    }
}
