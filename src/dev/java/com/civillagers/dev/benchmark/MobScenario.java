package com.civillagers.dev.benchmark;

import com.civillagers.ai.GuardTargetGoal;
import com.civillagers.dev.platform.DevVanilla;
import com.civillagers.entity.CivEntities;
import com.civillagers.entity.GuardStats;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * What {@code count} mobs of one kind cost, in real server ticks, standing around a bell in the middle of
 * the area at noon: guards on patrol, guards in combat, guards with nobody near, or, for comparison,
 * vanilla villagers or iron golems in the same spot. Nobody joins a benchmark, so the patrol and combat
 * scenarios tell every guard a player is near ({@link PlayerPresence}): they measure a guard someone
 * watches, and the third one the idle guard.
 *
 * <p>Every kind is timed from the outside by {@link EntityTickTimer}, around each entity's whole tick:
 * its AI, its pathfinding and the movement and collision vanilla does for any mob. Guards are also timed
 * from the inside by {@link GuardStats}, which the mod keeps for {@code /civillagers debug stats}; the two
 * should agree. With combat on, zombies come at the guards from the edge every few seconds, up to as many
 * as there are guards; the zombies' own ticks are not counted.
 */
final class MobScenario implements Stage {
    enum Kind {
        GUARDS_PATROL("guards on patrol", CivEntities::guard, false, true),
        GUARDS_COMBAT("guards in combat", CivEntities::guard, true, true),
        GUARDS_IDLE("guards, nobody near", CivEntities::guard, false, false),
        VILLAGERS("vanilla villagers", DevVanilla::villager, false, null),
        IRON_GOLEMS("vanilla iron golems", DevVanilla::ironGolem, false, null);

        final String label;
        final Supplier<? extends EntityType<? extends Mob>> type;
        final boolean combat;
        /** Whether guards are told a player is near; null for vanilla mobs, which never ask. */
        final Boolean playerNear;

        Kind(String label, Supplier<? extends EntityType<? extends Mob>> type, boolean combat, Boolean playerNear) {
            this.label = label;
            this.type = type;
            this.combat = combat;
            this.playerNear = playerNear;
        }

        boolean guards() {
            return playerNear != null;
        }
    }

    /** Mobs start on a ring this far from the bell. */
    private static final int RING = 6;
    /** Zombies appear this far from the bell, give or take two blocks. */
    private static final int ZOMBIE_DISTANCE = 14;
    private static final int ZOMBIE_WAVE = 100;

    private final BenchmarkWorld world;
    private final Kind kind;
    private final int count;
    private final int warmup;
    private final int measured;
    private final Metrics.Samples outside = new Metrics.Samples();
    private final Metrics.Samples inside = new Metrics.Samples();
    private final List<Mob> mobs = new ArrayList<>();
    private final List<Mob> zombies = new ArrayList<>();
    private final RandomSource random = RandomSource.create(42L);
    private BlockPos center;
    private int tick = -1;
    private long scansBefore;
    private long guardTicksBefore;
    private long entityTicks;
    private int fewestTicking = Integer.MAX_VALUE;
    private int zombiesSpawned;
    private JsonObject result;

    MobScenario(BenchmarkWorld world, Kind kind, int count, int warmup, int measured) {
        this.world = world;
        this.kind = kind;
        this.count = count;
        this.warmup = warmup;
        this.measured = measured;
    }

    @Override
    public String name() {
        return kind.label + " x" + count;
    }

    @Override
    public String progress() {
        return Math.max(0, tick) + "/" + (warmup + measured) + " server ticks";
    }

    @Override
    public boolean run(long deadlineNanos) {
        if (tick < 0) {
            start();
            tick = 0;
            return false;
        }
        // This runs at the end of the server tick, after the mobs have ticked and GuardStats has closed it.
        long outsideNanos = EntityTickTimer.drainNanos();
        int ticking = EntityTickTimer.drainEntities();
        if (tick == warmup) {
            scansBefore = GuardStats.scans();
            guardTicksBefore = GuardStats.guardTicks();
        }
        if (tick >= warmup) {
            outside.add(outsideNanos);
            entityTicks += ticking;
            fewestTicking = Math.min(fewestTicking, ticking);
            if (kind.guards()) inside.add(GuardStats.lastTickNanos());
        }
        if (kind.combat && tick % ZOMBIE_WAVE == 0) spawnZombies();
        tick++;
        if (tick < warmup + measured) return false;
        finish();
        return true;
    }

    private void start() {
        // Noon, so guards patrol rather than look for beds. The command sets every version's clock.
        world.server.getCommands().performPrefixedCommand(
                world.server.createCommandSourceStack().withSuppressedOutput(), "time set noon");
        center = world.center();
        world.place(center, Blocks.BELL.defaultBlockState());
        world.addLevelAnchor(center);
        PlayerPresence.force(kind.playerNear);
        EntityType<? extends Mob> type = kind.type.get();
        EntityTickTimer.watch(type);
        for (int i = 0; i < count; i++) {
            double angle = 2 * Math.PI * i / count;
            BlockPos at = world.surface(center.getX() + (int) Math.round(RING * Math.cos(angle)),
                    center.getZ() + (int) Math.round(RING * Math.sin(angle)));
            Mob mob = world.track(type.spawn(world.level, at, EntitySpawnReason.MOB_SUMMONED));
            if (mob == null) throw new IllegalStateException("could not spawn " + kind.label + " " + i + " at " + at.toShortString());
            mobs.add(mob);
        }
    }

    private void spawnZombies() {
        zombies.removeIf(zombie -> !zombie.isAlive());
        int wave = Math.min(count / 2 + 1, count - zombies.size());
        for (int i = 0; i < wave; i++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            int distance = ZOMBIE_DISTANCE - 2 + random.nextInt(5);
            BlockPos at = world.surface(center.getX() + (int) Math.round(distance * Math.cos(angle)),
                    center.getZ() + (int) Math.round(distance * Math.sin(angle)));
            Mob zombie = world.track(DevVanilla.zombie().spawn(world.level, at, EntitySpawnReason.MOB_SUMMONED));
            if (zombie == null) continue;
            zombies.add(zombie);
            zombiesSpawned++;
        }
    }

    private void finish() {
        EntityTickTimer.watch(null);
        PlayerPresence.force(null);
        int alive = 0;
        for (Mob mob : mobs) if (mob.isAlive()) alive++;
        // A mob that did not tick is not being measured: the scenario says so rather than look cheap.
        if (fewestTicking < alive) {
            throw new IllegalStateException(name() + ": only " + fewestTicking + " of " + alive + " ticked on some tick");
        }
        JsonObject json = new JsonObject();
        json.addProperty("kind", kind.name().toLowerCase(java.util.Locale.ROOT));
        json.addProperty("mobs", count);
        json.addProperty("combat", kind.combat);
        if (kind.guards()) json.addProperty("playerNear", kind.playerNear);
        json.addProperty("measuredTicks", outside.size());
        json.add("msPerTick", outside.summary(1_000_000.0));
        json.addProperty("microsPerMobPerTick", entityTicks == 0 ? 0.0 : Metrics.round(outside.sum() / 1000.0 / entityTicks));
        if (kind.guards()) {
            long guardTicks = GuardStats.guardTicks() - guardTicksBefore;
            json.add("msPerTickFromGuardStats", inside.summary(1_000_000.0));
            json.addProperty("scansPerGuardPer10Ticks", guardTicks == 0 ? 0.0
                    : Metrics.round((GuardStats.scans() - scansBefore) * (double) GuardTargetGoal.SCAN_INTERVAL / guardTicks));
        }
        json.addProperty("aliveAtEnd", alive);
        if (kind.combat) json.addProperty("zombiesSpawned", zombiesSpawned);
        result = json;
        world.discardEntities();
        mobs.clear();
        zombies.clear();
    }

    Kind kind() {
        return kind;
    }

    JsonObject result() {
        return result;
    }
}
