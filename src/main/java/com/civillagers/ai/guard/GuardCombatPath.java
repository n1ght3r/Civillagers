package com.civillagers.ai.guard;

import com.civillagers.ai.GuardPathBudget;
import com.civillagers.entity.guard.Guard;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/**
 * When a fighting guard searches a new path to its target. A search is most of what a fight costs, so a
 * guard searches again only once its target has moved more than {@link #MOVED} blocks from where the last
 * path led, or that path has ended short of it; never twice within {@link #MIN_INTERVAL} ticks; and only
 * within {@link GuardPathBudget#COMBAT_PER_TICK} searches per server tick across every guard. In between,
 * vanilla's navigation follows the path it has. One per fighting goal, a few numbers, no reference kept
 * to the target.
 *
 * <p><b>Water.</b> A path through water is a poor guide: its nodes sit on the surface, a guard afloat
 * drifts off them and turns round and round to find them again, and from a bank the search often ends at
 * the edge. So a guard that is in water, or close to a target in water, and sees its target, swims
 * straight at it instead ({@link #SWIM_RANGE}), steering every tick with no search at all.
 */
public final class GuardCombatPath {
    /** How far the target moves, in blocks, before the path to it is searched again. */
    public static final double MOVED = 2.0;
    private static final double MOVED_SQR = MOVED * MOVED;
    /** The fewest ticks between two searches by one guard. */
    public static final int MIN_INTERVAL = 10;
    /** Added to the wait when the target is far, beyond {@link #FAR_SQR}: the path matters less there. */
    private static final int FAR_EXTRA = 10;
    private static final double FAR_SQR = 16.0 * 16.0;
    /** Added to the wait when no path was found: searching again at once would find none either. */
    private static final int FAILED_EXTRA = 15;
    /** How near, in blocks, a target in sight is swum to straight rather than searched a path to. */
    public static final double SWIM_RANGE = 12.0;
    private static final double SWIM_RANGE_SQR = SWIM_RANGE * SWIM_RANGE;
    /** How near the water's edge, in blocks, a guard on dry land steps straight in after a target in the water. */
    private static final double SHORE_RANGE_SQR = 4.0 * 4.0;
    /**
     * The least speed modifier a guard swims at: a mob's swimming pace, no faster. 1.21.1 rounds to nothing
     * each part of a mob's motion under 0.003 a tick, and a slow guard swimming slantwise, below this,
     * stayed just under it and only crept along.
     */
    public static final double SWIM_SPEED = 0.6;
    /** Below this squared horizontal distance, a target is right over or under the guard. */
    public static final double OVERHEAD_SQR = 0.3 * 0.3;

    private final Guard guard;
    private boolean pathed;
    private double pathedX;
    private double pathedY;
    private double pathedZ;
    private int wait;
    private int searches;

    public GuardCombatPath(Guard guard) {
        this.guard = guard;
    }

    /** Forgets the last path, so the next {@link #follow} searches at once. Called when a fight starts. */
    public void reset() {
        pathed = false;
        wait = 0;
    }

    /**
     * Called every tick a goal wants its guard to close in on {@code target}; searches a path to it at
     * {@code speed} when the rules above allow, and returns whether it did.
     */
    public boolean follow(LivingEntity target, double speed) {
        if (swimsStraight(target)) {
            guard.getNavigation().stop();
            double dx = target.getX() - guard.getX();
            double dz = target.getZ() - guard.getZ();
            // Right over or under it, steering at it would only turn the guard round and round.
            if (dx * dx + dz * dz > OVERHEAD_SQR) guard.getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(),
                    Math.max(speed, SWIM_SPEED));
            // Out of the water again, it searches a path at once.
            pathed = false;
            wait = 0;
            return false;
        }
        if (wait > 0) {
            wait--;
            return false;
        }
        boolean moved = !pathed || target.distanceToSqr(pathedX, pathedY, pathedZ) > MOVED_SQR;
        if (!moved && !guard.getNavigation().isDone()) return false;
        ServerLevel level = guard.serverLevel();
        // Refused: the tick's searches are spent, so try on the next one.
        if (level == null || !GuardPathBudget.tryTakeCombat(level.getGameTime())) return false;
        searches++;
        pathed = true;
        pathedX = target.getX();
        pathedY = target.getY();
        pathedZ = target.getZ();
        wait = MIN_INTERVAL;
        if (guard.distanceToSqr(target) > FAR_SQR) wait += FAR_EXTRA;
        if (!guard.getNavigation().moveTo(target, speed)) wait += FAILED_EXTRA;
        return true;
    }

    /**
     * Whether the guard goes straight for {@code target} through the water: it is in water, or on the
     * shore next to a target in water, and it sees the target within {@link #SWIM_RANGE}. The sight is the
     * one the guard's senses keep for the tick, so no extra ray cast.
     */
    public boolean swimsStraight(LivingEntity target) {
        if (!guard.isInWater() && !target.isInWater()) return false;
        double distance = guard.distanceToSqr(target);
        if (distance > (guard.isInWater() ? SWIM_RANGE_SQR : SHORE_RANGE_SQR)) return false;
        return guard.getSensing().hasLineOfSight(target);
    }

    /** Path searches made since this was created, for the gametests. */
    public int searches() {
        return searches;
    }
}
