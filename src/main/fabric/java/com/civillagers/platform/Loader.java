package com.civillagers.platform;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Every call into the mod loader, for Fabric.
 *
 * <p>Each loader has its own copy of this class, with the same name and the same signatures, in its own
 * source directory; the build compiles exactly one of them. Common code calls it without knowing which
 * one. See {@code platform/AGENTS.md}.
 */
public final class Loader {
    private Loader() { }

    /** True under every development run task, false in the jar players install. */
    public static boolean isDevelopmentEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    /** The directory config files are read from and written to. */
    public static Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    public static boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    /**
     * Runs {@code registration} when {@code registry} accepts new entries.
     *
     * <p>Fabric leaves the built-in registries open while mods initialize, so this runs it straight away
     * and {@code registry} only matters to loaders that freeze them first.
     */
    public static void onRegister(ResourceKey<? extends Registry<?>> registry, Runnable registration) {
        registration.run();
    }

    /** Runs {@code action} once a server has finished starting and its levels are loaded. */
    public static void onServerStarted(Consumer<MinecraftServer> action) {
        ServerLifecycleEvents.SERVER_STARTED.register(action::accept);
    }

    /**
     * Runs {@code action} once a server has stopped and saved. Every cache the mod keeps is cleared from
     * here: in singleplayer each world opened is a new server, and anything left over leaks.
     */
    public static void onServerStopped(Consumer<MinecraftServer> action) {
        ServerLifecycleEvents.SERVER_STOPPED.register(action::accept);
    }

    /** Runs {@code action} at the end of every server tick, after every level has ticked. */
    public static void onServerTickEnd(Consumer<MinecraftServer> action) {
        ServerTickEvents.END_SERVER_TICK.register(action::accept);
    }

    /** Runs {@code action} with the server's command dispatcher whenever commands are (re)built. */
    public static void onRegisterCommands(Consumer<CommandDispatcher<CommandSourceStack>> action) {
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> action.accept(dispatcher));
    }
}
