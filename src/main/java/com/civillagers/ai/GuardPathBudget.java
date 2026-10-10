package com.civillagers.ai;

/**
 * Caps how many paths guards start in one server tick. Unhurried paths (walking to a patrol point, to a
 * bed, to a raid) and fights have a cap each, so a village full of patrols never keeps a guard from
 * chasing a zombie. Each is a full path search, and a dozen guards choosing their next patrol point, or
 * chasing a pack of zombies, on the same tick would otherwise all search at once. A guard refused here
 * tries again on a later tick.
 *
 * <p>A few numbers, reset on the first request of each tick, never a reference to a world. Cleared with
 * the mod's other caches all the same.
 */
public final class GuardPathBudget {
    /** New unhurried paths allowed per server tick, across every guard in every level. */
    public static final int PER_TICK = Integer.getInteger("civillagers.guard.pathsPerTick", 4);
    /** New paths towards a target allowed per server tick, across every guard in every level. */
    public static final int COMBAT_PER_TICK = Integer.getInteger("civillagers.guard.combatPathsPerTick", 8);

    private static long tick = Long.MIN_VALUE;
    private static int used;
    private static long refused;
    private static long combatTick = Long.MIN_VALUE;
    private static int combatUsed;
    private static long combatRefused;

    private GuardPathBudget() { }

    /** Takes one unhurried path from this tick's budget. Returns false, and the caller waits, when it is spent. */
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

    /** Takes one path towards a target from this tick's budget. Returns false when it is spent. */
    public static boolean tryTakeCombat(long gameTime) {
        if (gameTime != combatTick) {
            combatTick = gameTime;
            combatUsed = 0;
        }
        if (combatUsed >= COMBAT_PER_TICK) {
            combatRefused++;
            return false;
        }
        combatUsed++;
        return true;
    }

    /** Unhurried paths refused since the server started, for {@code /civillagers debug stats}. */
    public static long refused() {
        return refused;
    }

    /** Paths towards a target refused since the server started. */
    public static long combatRefused() {
        return combatRefused;
    }

    public static void clear() {
        tick = Long.MIN_VALUE;
        used = 0;
        refused = 0;
        combatTick = Long.MIN_VALUE;
        combatUsed = 0;
        combatRefused = 0;
    }
}
