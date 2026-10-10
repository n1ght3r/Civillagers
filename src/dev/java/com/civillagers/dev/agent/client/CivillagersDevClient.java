package com.civillagers.dev.agent.client;

import com.civillagers.dev.CivillagersDev;
import com.civillagers.dev.agent.game.CivAgent;
import com.civillagers.dev.platform.DevClientLoader;
import net.minecraft.client.Minecraft;

/**
 * What the dev tools register on a client, kept apart from {@link CivillagersDev} because a dedicated
 * server must never load a class that names {@link Minecraft}. The queue is polled on the client tick,
 * and {@code server.*} commands reach the integrated server from there.
 */
public final class CivillagersDevClient {
    private static boolean started;
    private static long ticks;

    private CivillagersDevClient() { }

    public static void initialize() {
        if (!CivillagersDev.enabled()) return;
        CivAgent.install(ClientProbes::register, () -> Minecraft.getInstance().stop());
        // Opened on the first tick rather than at initialization: the window and framebuffer exist by then.
        DevClientLoader.onClientTickEnd(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (!started) {
                started = true;
                ClientWindow.open(minecraft);
                CivAgent.start();
            }
            CivAgent.tick();
            ClientWindow.showStatus(minecraft, CivAgent.status(), ++ticks);
        });
    }
}
