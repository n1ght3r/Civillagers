package com.civillagers.dev.benchmark;

import com.civillagers.Civillagers;
import com.civillagers.village.Villages;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

import java.util.UUID;

/**
 * What one village's data takes in memory, against the roadmap's budget of about 4 KB. A typical village
 * here has 3 bells, 20 villagers, 4 guards and 10 kinds of item in its stockpile.
 *
 * <p>{@link #HELD} such villages are loaded into one set, the way a level holds them, and the heap is
 * measured after a full collection before and after; the difference divided by their number is what each
 * keeps, its share of the chunk index included, give or take what other threads allocated meanwhile.
 */
final class MemoryProbe implements Stage {
    private static final int HELD = 5_000;
    private static final int MEMBERS = 20;
    private static final int GUARDS = 4;
    private static final int STOCKPILE_KINDS = 10;

    private JsonObject result;

    @Override
    public String name() {
        return "memory";
    }

    @Override
    public boolean run(long deadlineNanos) {
        long heapBefore = Metrics.heapUsedAfterGcBytes();
        // The saved data is let go before the second measurement, so only the villages remain of it.
        JsonObject data = typicalVillages(HELD);
        long allocatedBefore = Metrics.allocatedBytes();
        Villages villages = Villages.CODEC.parse(JsonOps.INSTANCE, data).getOrThrow();
        // The runtime lookups are part of what a village costs while its level is loaded.
        villages.lookupSize();
        long allocated = Metrics.allocatedBytes() - allocatedBefore;
        data = null;
        long heapAfter = Metrics.heapUsedAfterGcBytes();
        if (villages.size() != HELD) throw new IllegalStateException("memory probe loaded " + villages.size() + " villages");

        Tag one = Villages.CODEC.encodeStart(NbtOps.INSTANCE,
                Villages.CODEC.parse(JsonOps.INSTANCE, typicalVillages(1)).getOrThrow()).getOrThrow();
        JsonObject json = new JsonObject();
        json.addProperty("typicalVillage", "3 bells, " + MEMBERS + " members, " + GUARDS + " guards, "
                + STOCKPILE_KINDS + " stockpile kinds");
        json.addProperty("villagesHeld", HELD);
        json.addProperty("retainedBytesPerVillage", Metrics.round((heapAfter - heapBefore) / (double) HELD));
        json.addProperty("allocatedBytesPerVillageLoaded", Metrics.round(allocated / (double) HELD));
        json.addProperty("savedSnbtCharactersPerVillage", one.toString().length());
        result = json;
        // Held until here so the collection above could not take it.
        if (villages.isEmpty()) throw new IllegalStateException("unreachable");
        return true;
    }

    /** Saved data for {@code count} typical villages, 1000 blocks apart so none would link up. */
    private static JsonObject typicalVillages(int count) {
        JsonArray list = new JsonArray();
        for (int v = 0; v < count; v++) {
            JsonObject village = new JsonObject();
            village.addProperty("id", v + 1);
            JsonArray anchors = new JsonArray();
            int x = v * 1000;
            anchors.add(pos(x, 64, 0));
            anchors.add(pos(x + 20, 64, 0));
            anchors.add(pos(x, 64, 20));
            village.add("anchors", anchors);
            village.add("members", uuids(v, 0, MEMBERS));
            village.add("guards", uuids(v, 1000, GUARDS));
            JsonObject stockpile = new JsonObject();
            for (int i = 0; i < STOCKPILE_KINDS; i++) stockpile.addProperty(Civillagers.id("item_" + i).toString(), 10 + i);
            village.add("stockpile", stockpile);
            list.add(village);
        }
        JsonObject data = new JsonObject();
        data.addProperty("next_id", count + 1);
        data.add("villages", list);
        return data;
    }

    private static JsonArray pos(int x, int y, int z) {
        JsonArray pos = new JsonArray();
        pos.add(x);
        pos.add(y);
        pos.add(z);
        return pos;
    }

    private static JsonArray uuids(int village, int first, int count) {
        JsonArray list = new JsonArray();
        for (int i = 0; i < count; i++) {
            list.add(UUIDUtil.CODEC.encodeStart(JsonOps.INSTANCE, new UUID(village, first + i)).getOrThrow());
        }
        return list;
    }

    JsonObject result() {
        return result;
    }
}
