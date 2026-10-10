package com.civillagers.ai;

import com.civillagers.entity.Guard;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/**
 * When a fighting guard searches a new path to its target. A search is most of what a fight costs, so a
 * guard searches again only once its target has moved more than {@link #MOVED} blocks from where the last
 * path led, or that path has ended short of it; never twice within {@link #MIN_INTERVAL} ticks; and only
 * within {@link GuardPathBudget#COMBAT_PER_TICK} searches per server tick across every guard. In between,
 * vanilla's navigation follows the path it has. One per fighting goal, a few numbers, no reference kept
 * to the target.
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

    /** Path searches made since this was created, for the gametests. */
    public int searches() {
        return searches;
    }
}
