package com.civillagers.ai;

/**
 * Caps how many unhurried paths guards start in one server tick: walking to a patrol point, to a bed, to
 * a raid. Each is a full path search, and a dozen guards choosing their next patrol point on the same
 * tick would otherwise all search at once. A guard refused here tries again on a later tick; fights are
 * not capped, since vanilla's melee goal already spaces out its own searches.
 *
 * <p>Two numbers, reset on the first request of each tick, never a reference to a world. Cleared with the
 * mod's other caches all the same.
 */
public final class GuardPathBudget {
    /** New unhurried paths allowed per server tick, across every guard in every level. */
    public static final int PER_TICK = Integer.getInteger("civillagers.guard.pathsPerTick", 4);

    private static long tick = Long.MIN_VALUE;
    private static int used;
    private static long refused;

    private GuardPathBudget() { }

    /** Takes one path from this tick's budget. Returns false, and the caller waits, when it is spent. */
    public static boolean tryTake(long gameTime) {
        if (gameTime != tick) {
            tick = gameTime;
            used = 0;
        }
        if (used >= PER_TICK) {
            refused++;
            return false;
        }
        used++;
        return true;
    }

    /** Paths refused since the server started, for {@code /civillagers debug stats}. */
    public static long refused() {
        return refused;
    }

    public static void clear() {
        tick = Long.MIN_VALUE;
        used = 0;
        refused = 0;
    }
}
