package com.civillagers;

import com.civillagers.ai.GuardPathBudget;
import com.civillagers.command.CivillagersCommands;
import com.civillagers.entity.CivEntities;
import com.civillagers.entity.guard.GuardStats;
import com.civillagers.platform.Loader;
import com.civillagers.village.StockpileKeys;
import com.civillagers.village.VillageStats;
import com.civillagers.village.VillageTicker;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Civillagers {
    public static final String MOD_ID = "civillagers";
    /** The Minecraft version this jar was built against; substituted per node at build time. */
    public static final String MINECRAFT = /*$ minecraft*/ "26.3";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private Civillagers() { }

    /**
     * Called once by the loader's entrypoint. Everything the mod registers and hooks starts here.
     *
     * <p>Registration goes through {@code Loader.onRegister(Registries.X, ...)}, never straight into a
     * registry: NeoForge freezes the registries before mods start and only opens them during its
     * registration event.
     */
    public static void initialize() {
        CivEntities.register();
        Loader.onServerStarted(server -> clearCaches());
        Loader.onServerStopped(server -> clearCaches());
        Loader.onServerTickEnd(VillageTicker::onServerTick);
        Loader.onServerTickEnd(server -> GuardStats.endTick());
        Loader.onRegisterCommands(CivillagersCommands::register);
        LOGGER.info("Civillagers initialized for Minecraft {}", MINECRAFT);
    }

    /**
     * Empties every cache the mod keeps outside a level. Village data itself lives in each level's saved
     * data and goes with the level; a new static cache gets its line here in the same change.
     */
    public static void clearCaches() {
        VillageStats.clear();
        GuardStats.clear();
        GuardPathBudget.clear();
        StockpileKeys.clear();
    }

    /** Entries held by the caches {@link #clearCaches()} empties; 0 right after it. */
    public static int cachedEntries() {
        return VillageStats.samples() + GuardStats.samples() + (GuardStats.scans() > 0 ? 1 : 0)
                + (GuardPathBudget.refused() > 0 ? 1 : 0) + (GuardPathBudget.combatRefused() > 0 ? 1 : 0)
                + StockpileKeys.size();
    }

    /** An id in the mod's namespace. */
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
