package com.civillagers.village;

import com.civillagers.platform.CodecSavedData;
import com.civillagers.platform.Vanilla;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Every village in one level, saved with the level. Owned by the level's data storage, so it is freed
 * with the level and nothing here outlives a world; nothing in the mod keeps a {@code Villages} in a
 * static.
 *
 * <p>Besides the saved villages it keeps three lookups, rebuilt from them whenever the set of bells
 * changes and never saved: the {@link VillageIndex} from chunk to village, the owner of each bell, and the
 * {@link VillageScheduler}'s slots.
 *
 * <p>The invariant: bells within {@link Village#LINK_DISTANCE} of each other are in one village, and each
 * village's bells are connected by such links. {@link #addAnchor} merges villages a new bell joins, and
 * {@link #removeAnchor} splits a village whose bells are no longer connected.
 */
public final class Villages extends CodecSavedData<Villages> {
    public static final Codec<Villages> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("next_id", 1).forGetter(villages -> villages.nextId),
            Village.CODEC.listOf().optionalFieldOf("villages", List.of()).forGetter(Villages::sortedVillages)
    ).apply(instance, Villages::new));

    private static final Vanilla.DataKind<Villages> KIND = new Vanilla.DataKind<>("villages", Villages::new, CODEC);

    private final Int2ObjectOpenHashMap<Village> byId = new Int2ObjectOpenHashMap<>();
    private int nextId = 1;

    // Runtime lookups, rebuilt from byId, never saved.
    private final VillageIndex index = new VillageIndex();
    private final Long2IntOpenHashMap anchorOwners = new Long2IntOpenHashMap();
    private final VillageScheduler scheduler = new VillageScheduler();
    private boolean lookupsStale = true;
    /** Which player discovery looks around next. */
    int discoveryCursor;

    /** An empty set of villages, not attached to any level. Tests and the benchmark use their own. */
    public Villages() { }

    private Villages(int nextId, List<Village> villages) {
        for (Village village : villages) {
            if (village.anchorCount() > 0) byId.put(village.id(), village);
            this.nextId = Math.max(this.nextId, village.id() + 1);
        }
        this.nextId = Math.max(this.nextId, nextId);
    }

    /** The villages of {@code level}, loaded from disk on first use. */
    public static Villages get(ServerLevel level) {
        return Vanilla.savedData(level, KIND);
    }

    @Override
    protected Codec<Villages> codec() {
        return CODEC;
    }

    public int size() {
        return byId.size();
    }

    public boolean isEmpty() {
        return byId.isEmpty();
    }

    public Village byId(int id) {
        return byId.get(id);
    }

    public Collection<Village> all() {
        return Collections.unmodifiableCollection(byId.values());
    }

    /** The villages in id order, for saving and for output that should not shuffle between runs. */
    public List<Village> sortedVillages() {
        List<Village> list = new ArrayList<>(byId.values());
        list.sort(Comparator.comparingInt(Village::id));
        return list;
    }

    /** The village whose radius covers {@code pos}, or null. Hash lookups, never a loop over every village. */
    public Village villageAt(BlockPos pos) {
        refreshLookups();
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        Village village = byId.get(index.villageAt(chunkX, chunkZ));
        if (village != null && village.contains(pos)) return village;
        // The chunk is shared with another village whose center is farther away.
        IntArrayList others = index.othersAt(chunkX, chunkZ);
        if (others == null) return null;
        for (int i = 0; i < others.size(); i++) {
            Village other = byId.get(others.getInt(i));
            if (other != null && other.contains(pos)) return other;
        }
        return null;
    }

    /** The village that has the bell at {@code pos}, or null. */
    public Village byAnchor(long pos) {
        refreshLookups();
        return byId.get(anchorOwners.get(pos));
    }

    public VillageIndex index() {
        refreshLookups();
        return index;
    }

    public VillageScheduler scheduler() {
        refreshLookups();
        return scheduler;
    }

    /**
     * Adds a bell. It joins the village it links to, or founds a new one; if it links several villages
     * they merge into the oldest. Returns the village the bell ended up in, which is the one it already
     * belonged to if it was known, or null if every linked village is full of bells.
     */
    public Village addAnchor(BlockPos pos, long gameTime) {
        long packed = pos.asLong();
        Village owner = byAnchor(packed);
        if (owner != null) return owner;

        IntArrayList linked = new IntArrayList();
        for (Village village : byId.values()) {
            if (village.linksTo(packed)) linked.add(village.id());
        }
        Village target;
        if (linked.isEmpty()) {
            target = new Village(nextId++, gameTime);
            byId.put(target.id(), target);
        } else {
            linked.sort(null);
            target = byId.get(linked.getInt(0));
            for (int i = 1; i < linked.size(); i++) target.absorb(byId.remove(linked.getInt(i)));
        }
        target.addAnchor(packed);
        changed();
        return target.hasAnchor(packed) ? target : null;
    }

    /**
     * Removes a bell. A village left with no bells is disbanded; one whose bells no longer link up splits,
     * the largest group keeping the village and every other group founding a new one.
     */
    public void removeAnchor(Village village, long pos, long gameTime) {
        if (!village.removeAnchor(pos)) return;
        if (village.anchorCount() == 0) {
            byId.remove(village.id());
            changed();
            return;
        }
        List<LongArrayList> groups = VillageClusters.group(village.anchors(), Village.LINK_DISTANCE);
        if (groups.size() > 1) {
            // The largest group keeps the village, its members and its stockpile; the others start afresh
            // and collect their own villagers on their next update.
            groups.sort((a, b) -> Integer.compare(b.size(), a.size()));
            for (int g = 1; g < groups.size(); g++) {
                LongArrayList group = groups.get(g);
                Village split = new Village(nextId++, gameTime);
                for (int i = 0; i < group.size(); i++) {
                    village.removeAnchor(group.getLong(i));
                    split.addAnchor(group.getLong(i));
                }
                byId.put(split.id(), split);
            }
        }
        changed();
    }

    /** Disbands a village outright. */
    public void remove(Village village) {
        if (byId.remove(village.id()) != null) changed();
    }

    /** Marks the data for saving after a change that does not move bells, such as new members. */
    public void contentChanged() {
        setDirty();
    }

    private void changed() {
        lookupsStale = true;
        setDirty();
    }

    private void refreshLookups() {
        if (!lookupsStale) return;
        lookupsStale = false;
        index.rebuild(byId);
        anchorOwners.clear();
        for (Int2ObjectMap.Entry<Village> entry : byId.int2ObjectEntrySet()) {
            Village village = entry.getValue();
            for (int i = 0; i < village.anchorCount(); i++) anchorOwners.put(village.anchorAt(i), village.id());
        }
        scheduler.invalidate();
    }

    /** How many entries the runtime lookups hold, for {@code /civillagers debug stats}. */
    public int lookupSize() {
        refreshLookups();
        return index.size() + anchorOwners.size() + scheduler.queued();
    }
}
