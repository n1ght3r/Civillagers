package com.civillagers.dev.benchmark;

import com.google.gson.JsonObject;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.Arrays;

/** Timing, allocation and heap helpers shared by every benchmark stage. */
final class Metrics {
    private static final com.sun.management.ThreadMXBean THREADS = allocationBean();

    private Metrics() { }

    /** Whether per-thread allocation counting works on this JVM. Without it every byte figure is zero. */
    static boolean allocationTracking() {
        return THREADS != null;
    }

    /** Bytes the current thread has allocated so far; the difference between two calls is exact. */
    static long allocatedBytes() {
        return THREADS != null ? Math.max(0L, THREADS.getCurrentThreadAllocatedBytes()) : 0L;
    }

    /** Heap in use after requesting a full collection. Approximate: other threads keep allocating. */
    static long heapUsedAfterGcBytes() {
        System.gc();
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
    }

    static long gcCount() {
        long total = 0;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            total += Math.max(0L, bean.getCollectionCount());
        }
        return total;
    }

    /** Three decimals are plenty for a report and keep the JSON readable. */
    static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    /** Four decimals, for figures in milliseconds that sit well below one. */
    static double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private static com.sun.management.ThreadMXBean allocationBean() {
        try {
            if (ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean bean
                    && bean.isThreadAllocatedMemorySupported()) {
                if (!bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
                return bean;
            }
        } catch (RuntimeException | LinkageError ignored) {
            // Not a HotSpot-compatible JVM; the report says allocationTracking=false.
        }
        return null;
    }

    /** A growable list of long samples with summary statistics. */
    static final class Samples {
        private long[] values = new long[256];
        private int size;

        void add(long value) {
            if (size == values.length) values = Arrays.copyOf(values, size * 2);
            values[size++] = value;
        }

        int size() {
            return size;
        }

        long sum() {
            long sum = 0;
            for (int i = 0; i < size; i++) sum += values[i];
            return sum;
        }

        double mean() {
            return size == 0 ? 0.0 : sum() / (double) size;
        }

        /** Mean, percentiles and maximum, each divided by {@code divisor} (1e6 turns nanoseconds into milliseconds). */
        JsonObject summary(double divisor) {
            long[] sorted = Arrays.copyOf(values, size);
            Arrays.sort(sorted);
            JsonObject json = new JsonObject();
            json.addProperty("mean", round4(mean() / divisor));
            json.addProperty("p50", round4(percentile(sorted, 0.50) / divisor));
            json.addProperty("p95", round4(percentile(sorted, 0.95) / divisor));
            json.addProperty("p99", round4(percentile(sorted, 0.99) / divisor));
            json.addProperty("max", round4((sorted.length == 0 ? 0 : sorted[sorted.length - 1]) / divisor));
            return json;
        }

        private static double percentile(long[] sorted, double fraction) {
            if (sorted.length == 0) return 0.0;
            int index = (int) Math.ceil(fraction * sorted.length) - 1;
            return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
        }
    }
}
