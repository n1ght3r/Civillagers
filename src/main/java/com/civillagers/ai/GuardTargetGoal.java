package com.civillagers.ai;

import com.civillagers.entity.Guard;
import com.civillagers.entity.GuardStats;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;

import java.util.EnumSet;

/**
 * Picks the nearest hostile mob a guard can see as its target. Looking is a box query plus a ray cast, so
 * a guard looks at most once every {@link #SCAN_INTERVAL} ticks, with a little jitter so guards spawned
 * together do not all look on the same tick. Vanilla's {@code NearestAttackableTargetGoal} looks on a
 * random tick instead, which averages the same rate but can look twice in a row. With no player near
 * ({@link Guard#playerNear()}) a guard looks every {@link #IDLE_SCAN_INTERVAL} ticks instead: what
 * nobody sees can wait a little, and whatever hurts the guard still turns it at once.
 */
public final class GuardTargetGoal extends TargetGoal {
    /** The fewest ticks between two scans by one guard. */
    public static final int SCAN_INTERVAL = 10;
    /** The fewest ticks between two scans by a guard with no player near. */
    public static final int IDLE_SCAN_INTERVAL = 40;
    /** Up to this many ticks are added to each wait, so guards drift apart. */
    private static final int SCAN_JITTER = 5;

    private final Guard guard;
    /** The guard's {@code tickCount} from which it may look again. */
    private int nextScan;
    /** Scans this goal has made, for the gametests. */
    private int scans;
    private LivingEntity found;

    public GuardTargetGoal(Guard guard) {
        super(guard, true);
        this.guard = guard;
        this.nextScan = guard.getRandom().nextInt(SCAN_INTERVAL);
        setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        // A guard with no weapon goes after nothing: it is a civilian until it is armed again.
        if (guard.getTarget() != null || guard.tickCount < nextScan || !guard.isCombatant()) return false;
        int interval = guard.playerNear() ? SCAN_INTERVAL : IDLE_SCAN_INTERVAL;
        nextScan = guard.tickCount + interval + guard.getRandom().nextInt(SCAN_JITTER + 1);
        GuardStats.recordScan();
        scans++;
        found = guard.findThreat();
        return found != null;
    }

    /** How many times this goal has looked for a target. */
    public int scans() {
        return scans;
    }

    @Override
    public void start() {
        guard.setTarget(found);
        targetMob = found;
        super.start();
    }

    @Override
    public void stop() {
        found = null;
        super.stop();
    }
}
