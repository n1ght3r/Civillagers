package com.civillagers.platform;

import com.civillagers.Civillagers;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every call into the mod loader, for NeoForge. The counterpart of the Fabric class of the same name;
 * see {@code platform/AGENTS.md}.
 */
public final class Loader {
    /** Registrations waiting for their registry's {@link RegisterEvent}, by registry. */
    private static final Map<ResourceKey<? extends Registry<?>>, List<Runnable>> PENDING = new HashMap<>();
    /** Registries whose event has fired; a registration for one of them is a bug, not a delay. */
    private static final Set<ResourceKey<? extends Registry<?>>> REGISTERED = new HashSet<>();
    private static boolean listening;

    private Loader() { }

    /** True under every development run task, false in the jar players install. */
    public static boolean isDevelopmentEnvironment() {
        // FML turned its environment fields into methods in NeoForge 21.9.
        //? if >=1.21.9 {
        return !FMLEnvironment.isProduction();
        //?} else {
        /*return !FMLEnvironment.production;
        *///?}
    }

    /** The directory config files are read from and written to. */
    public static Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    public static boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    /**
     * Runs {@code registration} when {@code registry} accepts new entries.
     *
     * <p>NeoForge freezes the built-in registries before mods are constructed and only opens each one
     * while it fires {@link RegisterEvent}, so the registration is queued and run from that event.
     */
    public static void onRegister(ResourceKey<? extends Registry<?>> registry, Runnable registration) {
        if (REGISTERED.contains(registry)) {
            throw new IllegalStateException("Registration for " + registry.identifier() + " arrived after its event");
        }
        if (!listening) {
            modBus().addListener(Loader::register);
            listening = true;
        }
        PENDING.computeIfAbsent(registry, key -> new ArrayList<>()).add(registration);
    }

    private static IEventBus modBus() {
        return ModList.get().getModContainerById(Civillagers.MOD_ID).orElseThrow().getEventBus();
    }

    private static void register(RegisterEvent event) {
        REGISTERED.add(event.getRegistryKey());
        List<Runnable> registrations = PENDING.remove(event.getRegistryKey());
        if (registrations != null) registrations.forEach(Runnable::run);
    }
}
