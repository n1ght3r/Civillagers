package com.civillagers;

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
        LOGGER.info("Civillagers initialized for Minecraft {}", MINECRAFT);
    }

    /** An id in the mod's namespace. */
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
