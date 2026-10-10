package com.civillagers.dev.benchmark;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Arrays;

/**
 * How much a benchmark run measures. Every profile runs the same scenarios; only the scale differs.
 *
 * @param villageCounts     villages per village scenario
 * @param villageWarmup     simulated ticks thrown away before a village scenario is measured
 * @param villageTicks      simulated ticks measured per village scenario
 * @param guardCounts       guards per guard scenario
 * @param guardWarmup       real server ticks thrown away before a guard scenario is measured
 * @param guardTicks        real server ticks measured per guard scenario
 */
record BenchmarkProfile(String name, int[] villageCounts, int villageWarmup, int villageTicks,
                        int[] guardCounts, int guardWarmup, int guardTicks) {
    /** The most villages a scenario can have: one per chunk of the benchmark area. */
    static final int MAX_VILLAGES = BenchmarkWorld.VILLAGE_GRID * BenchmarkWorld.VILLAGE_GRID;

    static final BenchmarkProfile QUICK = new BenchmarkProfile("quick",
            new int[] {1, 10, 50}, 200, 1_000, new int[] {1, 20}, 100, 300);
    static final BenchmarkProfile STANDARD = new BenchmarkProfile("standard",
            new int[] {1, 10, 50, 100}, 400, 2_000, new int[] {1, 10, 20, 50}, 100, 400);

    String describe() {
        return "villages " + Arrays.toString(villageCounts) + " x " + villageTicks + " simulated ticks, guards "
                + Arrays.toString(guardCounts) + " x " + guardTicks + " server ticks (patrol, combat, nobody near)";
    }

    JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("name", name);
        JsonArray villages = new JsonArray();
        for (int count : villageCounts) villages.add(count);
        json.add("villageCounts", villages);
        json.addProperty("villageWarmupTicks", villageWarmup);
        json.addProperty("villageMeasuredTicks", villageTicks);
        JsonArray guards = new JsonArray();
        for (int count : guardCounts) guards.add(count);
        json.add("guardCounts", guards);
        json.addProperty("guardWarmupTicks", guardWarmup);
        json.addProperty("guardMeasuredTicks", guardTicks);
        return json;
    }
}
