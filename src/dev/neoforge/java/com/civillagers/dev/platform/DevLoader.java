package com.civillagers.dev.platform;

import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.nio.file.Path;
import java.util.function.Consumer;

/** The NeoForge counterpart of the Fabric class of the same name. */
public final class DevLoader {
    /** Which loader this build compiled, for the benchmark report. */
    public static final String LOADER = "neoforge";

    private DevLoader() { }

    /** The run directory: {@code run/<node>}. */
    public static Path gameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    /** The version of a loaded mod, as the loader reports it. */
    public static String modVersion(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unknown");
    }

    /** Whether this process is a dedicated server, with no client classes to load. */
    public static boolean isDedicatedServer() {
        // FML turned its environment fields into methods in NeoForge 21.9.
        //? if >=1.21.9 {
        return net.neoforged.fml.loading.FMLEnvironment.getDist().isDedicatedServer();
        //?} else {
        /*return net.neoforged.fml.loading.FMLEnvironment.dist.isDedicatedServer();
        *///?}
    }

    /** Runs as the server shuts down, while the world is still there. */
    public static void onServerStopping(Consumer<MinecraftServer> handler) {
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> handler.accept(event.getServer()));
    }
}
