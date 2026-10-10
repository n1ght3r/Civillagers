package com.civillagers.dev.platform;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Every call into the mod loader the dev tools make and {@code com.civillagers.platform.Loader} does not,
 * for Fabric. One copy per loader, same name, same signatures, like the mod's own seam.
 */
public final class DevLoader {
    /** Which loader this build compiled, for the benchmark report. */
    public static final String LOADER = "fabric";

    private DevLoader() { }

    /** The run directory: {@code run/<node>}. */
    public static Path gameDir() {
        return FabricLoader.getInstance().getGameDir();
    }

    /** The version of a loaded mod, as the loader reports it. */
    public static String modVersion(String modId) {
        return FabricLoader.getInstance().getModContainer(modId)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    /** Whether this process is a dedicated server, with no client classes to load. */
    public static boolean isDedicatedServer() {
        return FabricLoader.getInstance().getEnvironmentType() == net.fabricmc.api.EnvType.SERVER;
    }

    /** Runs as the server shuts down, while the world is still there. */
    public static void onServerStopping(Consumer<MinecraftServer> handler) {
        ServerLifecycleEvents.SERVER_STOPPING.register(handler::accept);
    }
}
