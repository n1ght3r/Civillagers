package com.civillagers.dev.platform;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * The client half of {@link DevLoader}, for Fabric. Loaded only from the dev tools' client entrypoint, so
 * a dedicated server never reaches a class that names a client-only event.
 */
public final class DevClientLoader {
    private DevClientLoader() { }

    /** Runs at the end of every client tick, whether or not a world is loaded. */
    public static void onClientTickEnd(Runnable handler) {
        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> handler.run());
    }
}
