package com.civillagers.platform;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

import java.nio.file.Path;

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
}
