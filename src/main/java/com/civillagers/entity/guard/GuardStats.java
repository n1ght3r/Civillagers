package com.civillagers.entity.guard;

/**
 * What guards cost the server over the last {@link #WINDOW} ticks, for {@code /civillagers debug stats}
 * and the benchmark: the time spent in every guard's tick, summed per server tick, and how often guards
 * looked for a target. Plain numbers, nothing from a world, in a fixed-size ring buffer; cleared when a
 * server starts and when it stops, like every cache the mod keeps.
 *
 * <p>Guards add to the running tick from their own {@code tick()}; the end of the server tick closes it
 * with {@link #endTick()}. Server thread only.
 */
public final class GuardStats {
    /** Ticks the mean and the maximum are taken over: 10 seconds. */
    public static final int WINDOW = 200;

    private static final long[] TICK_NANOS = new long[WINDOW];
    private static int next;
    private static int filled;

    // The server tick still running.
    private static long tickNanos;
    private static int tickGuards;

    // Totals since the server started.
    private static long guardTicks;
    private static long scans;
    private static int lastGuards;
    private static long lastTickNanos;

    private GuardStats() { }

    /** One guard's tick took {@code nanos}. */
    static void recordGuardTick(long nanos) {
        tickNanos += nanos;
        tickGuards++;
    }

    /** One guard looked around for a target. */
    public static void recordScan() {
        scans++;
    }

    /** Closes the server tick: what every guard's tick cost in it goes into the window. */
    public static void endTick() {
        TICK_NANOS[next] = tickNanos;
        next = (next + 1) % WINDOW;
        if (filled < WINDOW) filled++;
        guardTicks += tickGuards;
        lastGuards = tickGuards;
        lastTickNanos = tickNanos;
        tickNanos = 0;
        tickGuards = 0;
    }

    /** Mean milliseconds per server tick all guards took together, over the window. */
    public static double meanMillis() {
        if (filled == 0) return 0;
        long sum = 0;
        for (int i = 0; i < filled; i++) sum += TICK_NANOS[i];
        return sum / (double) filled / 1_000_000.0;
    }

    /** The slowest server tick for guards in the window, in milliseconds. */
    public static double maxMillis() {
        long max = 0;
        for (int i = 0; i < filled; i++) max = Math.max(max, TICK_NANOS[i]);
        return max / 1_000_000.0;
    }

    /** Ticks recorded, up to {@link #WINDOW}. */
    public static int samples() {
        return filled;
    }

    /** Guards that ticked in the last closed server tick. */
    public static int lastGuards() {
        return lastGuards;
    }

    /** What every guard's tick cost together in the last closed server tick, in nanoseconds. */
    public static long lastTickNanos() {
        return lastTickNanos;
    }

    /** Guard ticks since the server started. */
    public static long guardTicks() {
        return guardTicks;
    }

    /** Target scans since the server started. */
    public static long scans() {
        return scans;
    }

    public static void clear() {
        java.util.Arrays.fill(TICK_NANOS, 0L);
        next = 0;
        filled = 0;
        tickNanos = 0;
        tickGuards = 0;
        guardTicks = 0;
        scans = 0;
        lastGuards = 0;
        lastTickNanos = 0;
    }
}
