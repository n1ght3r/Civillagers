package com.civillagers.village;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One village: the bells it grew around, the villagers and guards who belong to it, its level and its
 * stockpile.
 *
 * <p>A village is anchored by its bells (vanilla's meeting point POI). Its center is the middle of its
 * bells and its radius reaches {@link #MIN_RADIUS} past the farthest one, so both follow from the anchors
 * alone and never depend on which chunks happen to be loaded. Bells within {@link #LINK_DISTANCE} of each
 * other belong to one village; {@link Villages} merges and splits villages to keep it that way.
 *
 * <p>Members and guards are {@link UUID}s, never entities: an entity is looked up from its level when it is
 * needed and not kept past the tick. Every collection here has a hard cap.
 */
public final class Village {
    /** Bells closer than this, in blocks, belong to one village. */
    public static final int LINK_DISTANCE = 64;
    /** How far a village reaches past its farthest bell, and so its smallest radius. */
    public static final int MIN_RADIUS = 32;
    public static final int MAX_RADIUS = 128;
    public static final int MAX_ANCHORS = 64;
    public static final int MAX_MEMBERS = 128;
    public static final int MAX_GUARDS = 32;
    /** Distinct item kinds in the stockpile. */
    public static final int MAX_STOCKPILE_KINDS = 256;

    public static final Codec<Village> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("id").forGetter(Village::id),
            BlockPos.CODEC.listOf().fieldOf("anchors").forGetter(Village::anchorList),
            UUIDUtil.CODEC.listOf().optionalFieldOf("members", List.of()).forGetter(Village::members),
            Codec.INT.optionalFieldOf("level", 1).forGetter(Village::level),
            Codec.unboundedMap(Identifier.CODEC, Codec.INT).optionalFieldOf("stockpile", Map.of())
                    .forGetter(village -> Map.copyOf(village.stockpile)),
            Codec.INT.optionalFieldOf("beds", 0).forGetter(Village::beds),
            Codec.INT.optionalFieldOf("workstations", 0).forGetter(Village::workstations),
            Codec.LONG.optionalFieldOf("founded", 0L).forGetter(Village::founded),
            UUIDUtil.CODEC.listOf().optionalFieldOf("guards", List.of()).forGetter(Village::guards)
    ).apply(instance, Village::new));

    private final int id;
    /** Bell positions, as {@link BlockPos#asLong()}. */
    private final LongArrayList anchors = new LongArrayList();
    private final UuidList members = new UuidList(MAX_MEMBERS);
    private final UuidList guards = new UuidList(MAX_GUARDS);
    private final Object2IntOpenHashMap<Identifier> stockpile = new Object2IntOpenHashMap<>();
    private int level;
    private int beds;
    private int workstations;
    private final long founded;

    private BlockPos center = BlockPos.ZERO;
    private int radius = MIN_RADIUS;

    // Runtime state, never saved.
    /** Whether the scheduler has this village waiting in its queue. */
    boolean queued;
    /** The game time this village last ran its update, or -1. */
    long lastTicked = -1;
    /** How many updates it has run since it was loaded; picks which slower checks run this time. */
    int updates;

    Village(int id, long founded) {
        this.id = id;
        this.founded = founded;
        this.level = 1;
    }

    private Village(int id, List<BlockPos> anchors, List<UUID> members, int level, Map<Identifier, Integer> stockpile,
                    int beds, int workstations, long founded, List<UUID> guards) {
        this(id, founded);
        for (BlockPos anchor : anchors) {
            if (this.anchors.size() < MAX_ANCHORS) this.anchors.add(anchor.asLong());
        }
        for (UUID member : members) addMember(member);
        for (UUID guard : guards) addGuard(guard);
        for (Map.Entry<Identifier, Integer> entry : stockpile.entrySet()) addToStockpile(entry.getKey(), entry.getValue());
        this.level = Math.max(1, level);
        this.beds = Math.max(0, beds);
        this.workstations = Math.max(0, workstations);
        recomputeBounds();
    }

    public int id() {
        return id;
    }

    /** The middle of the village's bells. */
    public BlockPos center() {
        return center;
    }

    /** How far the village reaches from {@link #center()}, horizontally, in blocks. */
    public int radius() {
        return radius;
    }

    /** Whether {@code pos} is within the village's radius, horizontally. */
    public boolean contains(BlockPos pos) {
        return contains(pos.getX(), pos.getZ());
    }

    public boolean contains(int x, int z) {
        long dx = x - center.getX();
        long dz = z - center.getZ();
        return dx * dx + dz * dz <= (long) radius * radius;
    }

    public int level() {
        return level;
    }

    public int beds() {
        return beds;
    }

    public int workstations() {
        return workstations;
    }

    /** The game time the village was founded. */
    public long founded() {
        return founded;
    }

    public int anchorCount() {
        return anchors.size();
    }

    /** The {@code index}th bell, as {@link BlockPos#asLong()}. */
    public long anchorAt(int index) {
        return anchors.getLong(index);
    }

    public boolean hasAnchor(long pos) {
        return anchors.contains(pos);
    }

    public List<BlockPos> anchorList() {
        List<BlockPos> list = new ArrayList<>(anchors.size());
        for (int i = 0; i < anchors.size(); i++) list.add(BlockPos.of(anchors.getLong(i)));
        return list;
    }

    /** Whether {@code pos} is within {@link #LINK_DISTANCE} of one of this village's bells. */
    public boolean linksTo(long pos) {
        int x = BlockPos.getX(pos);
        int y = BlockPos.getY(pos);
        int z = BlockPos.getZ(pos);
        long limit = (long) LINK_DISTANCE * LINK_DISTANCE;
        for (int i = 0; i < anchors.size(); i++) {
            long anchor = anchors.getLong(i);
            long dx = BlockPos.getX(anchor) - x;
            long dy = BlockPos.getY(anchor) - y;
            long dz = BlockPos.getZ(anchor) - z;
            if (dx * dx + dy * dy + dz * dz <= limit) return true;
        }
        return false;
    }

    /** The members in the order they joined, as a copy: it allocates, so not for a tick. */
    public List<UUID> members() {
        return members.toList();
    }

    public int memberCount() {
        return members.size();
    }

    public boolean hasMember(UUID member) {
        return members.contains(member);
    }

    /** Adds a member, unless the village is full. Returns whether the members changed. */
    public boolean addMember(UUID member) {
        return members.add(member);
    }

    public boolean removeMember(UUID member) {
        return members.remove(member);
    }

    /** The guards who belong to the village: {@code Guard} entities, by UUID, like the members. */
    public List<UUID> guards() {
        return guards.toList();
    }

    public int guardCount() {
        return guards.size();
    }

    public boolean hasGuard(UUID guard) {
        return guards.contains(guard);
    }

    /** Adds a guard, unless the village has its fill. Returns whether the guards changed. */
    public boolean addGuard(UUID guard) {
        return guards.add(guard);
    }

    public boolean removeGuard(UUID guard) {
        return guards.remove(guard);
    }

    /** How many of {@code item} the stockpile holds. */
    public int stockpile(Identifier item) {
        return stockpile.getInt(item);
    }

    public Object2IntMap<Identifier> stockpile() {
        return it.unimi.dsi.fastutil.objects.Object2IntMaps.unmodifiable(stockpile);
    }

    /**
     * Adds {@code count} (negative to take) of {@code item}, never going below zero. Returns how many were
     * actually added or taken; a new kind of item past {@link #MAX_STOCKPILE_KINDS} is refused.
     */
    public int addToStockpile(Identifier item, int count) {
        int current = stockpile.getInt(item);
        if (current == 0 && count > 0 && stockpile.size() >= MAX_STOCKPILE_KINDS) return 0;
        int next = (int) Math.max(0, Math.min(Integer.MAX_VALUE, (long) current + count));
        if (next == 0) stockpile.removeInt(item);
        else stockpile.put(current == 0 ? StockpileKeys.intern(item) : item, next);
        return next - current;
    }

    // Mutators for Villages and the update, which keep the derived state and the index in step.

    boolean addAnchor(long pos) {
        if (anchors.size() >= MAX_ANCHORS || anchors.contains(pos)) return false;
        anchors.add(pos);
        recomputeBounds();
        return true;
    }

    boolean removeAnchor(long pos) {
        if (!anchors.rem(pos)) return false;
        recomputeBounds();
        return true;
    }

    LongArrayList anchors() {
        return anchors;
    }

    /** Takes in everything {@code other} has. Used when two villages merge; {@code other} is dropped after. */
    void absorb(Village other) {
        for (int i = 0; i < other.anchors.size(); i++) {
            long anchor = other.anchors.getLong(i);
            if (anchors.size() < MAX_ANCHORS && !anchors.contains(anchor)) anchors.add(anchor);
        }
        for (int i = 0; i < other.members.size(); i++) addMember(other.members.get(i));
        for (int i = 0; i < other.guards.size(); i++) addGuard(other.guards.get(i));
        for (Object2IntMap.Entry<Identifier> entry : other.stockpile.object2IntEntrySet()) {
            addToStockpile(entry.getKey(), entry.getIntValue());
        }
        level = Math.max(level, other.level);
        beds += other.beds;
        workstations += other.workstations;
        recomputeBounds();
    }

    /** Sets the POI counts. Returns whether they changed. */
    boolean setCounts(int beds, int workstations) {
        if (this.beds == beds && this.workstations == workstations) return false;
        this.beds = beds;
        this.workstations = workstations;
        return true;
    }

    /** Replaces the members with {@code found}, capped. Returns whether they changed. */
    boolean replaceMembers(Set<UUID> found) {
        if (members.sameAs(found)) return false;
        members.clear();
        for (UUID member : found) addMember(member);
        return true;
    }

    /** Replaces the guards with {@code found}, capped. Returns whether they changed. */
    boolean replaceGuards(Set<UUID> found) {
        if (guards.sameAs(found)) return false;
        guards.clear();
        for (UUID guard : found) addGuard(guard);
        return true;
    }

    private void recomputeBounds() {
        if (anchors.isEmpty()) {
            radius = MIN_RADIUS;
            return;
        }
        long sx = 0, sy = 0, sz = 0;
        for (int i = 0; i < anchors.size(); i++) {
            long anchor = anchors.getLong(i);
            sx += BlockPos.getX(anchor);
            sy += BlockPos.getY(anchor);
            sz += BlockPos.getZ(anchor);
        }
        int n = anchors.size();
        center = new BlockPos((int) Math.floorDiv(sx, n), (int) Math.floorDiv(sy, n), (int) Math.floorDiv(sz, n));
        long farthest = 0;
        for (int i = 0; i < anchors.size(); i++) {
            long anchor = anchors.getLong(i);
            long dx = BlockPos.getX(anchor) - center.getX();
            long dz = BlockPos.getZ(anchor) - center.getZ();
            farthest = Math.max(farthest, dx * dx + dz * dz);
        }
        radius = (int) Math.min(MAX_RADIUS, Math.ceil(Math.sqrt(farthest)) + MIN_RADIUS);
    }

    @Override
    public String toString() {
        return "Village#" + id + "@" + center.toShortString() + " r=" + radius;
    }
}
