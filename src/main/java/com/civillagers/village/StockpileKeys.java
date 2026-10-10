package com.civillagers.village;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.resources.Identifier;

/**
 * One shared instance of each item id that appears in a stockpile. Loading saved data makes a new id
 * object for every entry, so a hundred villages that each hold planks would otherwise hold a hundred
 * copies of {@code minecraft:oak_planks}.
 *
 * <p>Ids only, nothing from a world, and capped: past {@link #CAP} distinct ids a new one is used as it
 * comes. Emptied with the mod's other caches when a server starts and stops. Server thread only.
 */
public final class StockpileKeys {
    static final int CAP = 4096;

    private static final ObjectOpenHashSet<Identifier> KEYS = new ObjectOpenHashSet<>();

    private StockpileKeys() { }

    /** The shared instance equal to {@code id}. */
    public static Identifier intern(Identifier id) {
        if (KEYS.size() >= CAP) {
            Identifier known = KEYS.get(id);
            return known != null ? known : id;
        }
        return KEYS.addOrGet(id);
    }

    public static int size() {
        return KEYS.size();
    }

    public static void clear() {
        KEYS.clear();
        KEYS.trim();
    }
}
