package com.civillagers.village;

/**
 * What the mod's village work cost the server over the last {@link #WINDOW} ticks, for
 * {@code /civillagers debug stats}. Plain numbers, nothing from a world, in a fixed-size ring buffer;
 * cleared when a server starts and when it stops, like every cache the mod keeps.
 */
public final class VillageStats {
    /** Ticks the mean and the maximum are taken over: 10 seconds. */
    public static final int WINDOW = 200;

    private static final long[] TICK_NANOS = new long[WINDOW];
    private static int next;
    private static int filled;
    private static long updates;
    private static long discoveries;

    private VillageStats() { }

    /** Records one server tick's village work. */
    public static void recordTick(long nanos, int updated) {
        TICK_NANOS[next] = nanos;
        next = (next + 1) % WINDOW;
        if (filled < WINDOW) filled++;
        updates += updated;
    }

    static void recordDiscovery() {
        discoveries++;
    }

    /** Mean milliseconds per tick over the window. */
    public static double meanMillis() {
        if (filled == 0) return 0;
        long sum = 0;
        for (int i = 0; i < filled; i++) sum += TICK_NANOS[i];
        return sum / (double) filled / 1_000_000.0;
    }

    /** The slowest tick in the window, in milliseconds. */
    public static double maxMillis() {
        long max = 0;
        for (int i = 0; i < filled; i++) max = Math.max(max, TICK_NANOS[i]);
        return max / 1_000_000.0;
    }

    /** Ticks recorded, up to {@link #WINDOW}. */
    public static int samples() {
        return filled;
    }

    /** Village updates since the server started. */
    public static long updates() {
        return updates;
    }

    /** Discovery scans since the server started. */
    public static long discoveries() {
        return discoveries;
    }

    public static void clear() {
        java.util.Arrays.fill(TICK_NANOS, 0L);
        next = 0;
        filled = 0;
        updates = 0;
        discoveries = 0;
    }
}
