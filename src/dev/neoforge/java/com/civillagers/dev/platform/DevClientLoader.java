package com.civillagers.dev.platform;

import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** The client half of {@link DevLoader}, for NeoForge. Loaded only from the dev tools' client mod class. */
public final class DevClientLoader {
    private DevClientLoader() { }

    /** Runs at the end of every client tick, whether or not a world is loaded. */
    public static void onClientTickEnd(Runnable handler) {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> handler.run());
    }
}
