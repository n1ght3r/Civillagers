package com.civillagers.ai;

import com.civillagers.entity.Guard;
import com.civillagers.village.Village;
import com.civillagers.village.Villages;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.EnumSet;

/**
 * Walks a guard from one point of its village to another, pausing between them. Points are taken around
 * the village's bells, inside its radius, and never further than {@link #MAX_STEP} from where the guard
 * stands, so a path stays short; a guard outside any village keeps to the ground around it.
 *
 * <p>A point is chosen from the heightmap, never by scanning blocks, and only in a loaded chunk. Starting
 * the walk is one path search, taken from {@link GuardPathBudget}; after that vanilla's navigation
 * follows the path and only searches again when it is stuck. With no player near
 * ({@link Guard#playerNear()}) a guard stays where it is: walking is most of what a guard costs.
 */
public final class GuardPatrolGoal extends Goal {
    static final double SPEED = 0.5;
    /**
     * The farthest a patrol point is from the guard, in blocks. A path search is most of what a patrolling
     * guard costs, and its cost grows with the distance, so walks are short and a guard crosses the village
     * in several of them.
     */
    public static final int MAX_STEP = 16;
    /** Without a village, a guard wanders this close to where it stands. */
    private static final int LONE_STEP = 8;
    /** Ticks a guard stands at its post between two walks: at least the first, plus up to the second. */
    private static final int PAUSE = 160;
    private static final int PAUSE_JITTER = 240;
    /** Ticks before a refused or failed walk is tried again. */
    private static final int RETRY = 20;

    private final Guard guard;
    private final BlockPos.MutableBlockPos point = new BlockPos.MutableBlockPos();
    private int nextWalk;

    public GuardPatrolGoal(Guard guard) {
        this.guard = guard;
        this.nextWalk = guard.getRandom().nextInt(PAUSE);
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (guard.tickCount < nextWalk || guard.getTarget() != null || guard.isSleeping()) return false;
        ServerLevel level = guard.serverLevel();
        if (level == null) return false;
        nextWalk = guard.tickCount + RETRY;
        // A patrol is for players to see: with nobody near, the guard stands at its post.
        if (!guard.playerNear()) return false;
        if (!pickPoint(level, guard, guard.getRandom(), point)) return false;
        if (!GuardPathBudget.tryTake(level.getGameTime())) return false;
        if (!guard.getNavigation().moveTo(point.getX() + 0.5, point.getY(), point.getZ() + 0.5, SPEED)) return false;
        nextWalk = guard.tickCount + PAUSE + guard.getRandom().nextInt(PAUSE_JITTER);
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return !guard.getNavigation().isDone() && guard.getTarget() == null && !guard.isSleeping();
    }

    /**
     * Picks a patrol point for {@code guard} into {@code out}: around one of its village's bells, within
     * the village and within {@link #MAX_STEP} of the guard, on top of the ground, in a loaded chunk.
     * Returns false when there is none this time. Public for the gametests.
     */
    public static boolean pickPoint(ServerLevel level, Guard guard, RandomSource random, BlockPos.MutableBlockPos out) {
        BlockPos at = guard.blockPosition();
        Village village = Villages.get(level).villageAt(at);
        int x;
        int z;
        if (village != null) {
            long bell = village.anchorAt(random.nextInt(village.anchorCount()));
            int reach = Village.MIN_RADIUS * 3 / 4;
            x = BlockPos.getX(bell) + random.nextInt(2 * reach + 1) - reach;
            z = BlockPos.getZ(bell) + random.nextInt(2 * reach + 1) - reach;
            // Keep the walk short: step towards the point, at most MAX_STEP.
            int dx = x - at.getX();
            int dz = z - at.getZ();
            double length = Math.sqrt((double) dx * dx + (double) dz * dz);
            if (length > MAX_STEP) {
                x = at.getX() + (int) (dx * MAX_STEP / length);
                z = at.getZ() + (int) (dz * MAX_STEP / length);
            }
            if (!village.contains(x, z)) return false;
        } else {
            x = at.getX() + random.nextInt(2 * LONE_STEP + 1) - LONE_STEP;
            z = at.getZ() + random.nextInt(2 * LONE_STEP + 1) - LONE_STEP;
        }
        if (!level.hasChunk(x >> 4, z >> 4)) return false;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (Math.abs(y - at.getY()) > 8) return false;
        out.set(x, y, z);
        return true;
    }
}
