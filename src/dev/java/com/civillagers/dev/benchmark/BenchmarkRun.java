package com.civillagers.dev.benchmark;

import com.civillagers.Civillagers;
import com.civillagers.dev.platform.DevLoader;
import com.civillagers.village.VillageScheduler;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * One benchmark from start to report. Stages run in order over as many server ticks as they need; however
 * the run ends, completed, cancelled or failed, it restores the world and writes a report, with each of
 * the roadmap's budgets it measured marked met or missed.
 */
final class BenchmarkRun {
    /** Benchmark work per server tick, leaving the rest of the 50 ms to the server itself. */
    private static final long TICK_BUDGET_NANOS = 35_000_000L;
    /** Ticks for the points of interest of freshly placed bells to register: the server adds them as a task. */
    private static final int POI_SETTLE_TICKS = 5;
    private static final int GUARD_SETTLE_TICKS = 20;
    private static final double MIB = 1024.0 * 1024.0;

    // The budgets in docs/ROADMAP.md that this run measures.
    private static final int BUDGET_VILLAGES = 50;
    private static final double BUDGET_VILLAGES_MS = 0.1;
    private static final double BUDGET_UNLOADED_MS = 0.01;
    private static final int BUDGET_GUARDS = 20;
    private static final double BUDGET_GUARDS_MS = 0.15;
    private static final double BUDGET_SCANS_PER_10_TICKS = 1.0;
    private static final double BUDGET_VILLAGE_BYTES = 4096;

    private final MinecraftServer server;
    private final CommandSourceStack source;
    private final BenchmarkProfile profile;
    private final BenchmarkWorld world;
    private final Deque<Stage> stages = new ArrayDeque<>();
    private final int totalStages;
    private final List<VillageScenario> villageScenarios = new ArrayList<>();
    private final VillageScenario unloaded;
    private final MemoryProbe memory = new MemoryProbe();
    private final List<MobScenario> mobScenarios = new ArrayList<>();
    private final long startedNanos = System.nanoTime();
    private final String startedAt = OffsetDateTime.now().toString();
    private final long gcBefore = Metrics.gcCount();
    private String cancelReason;
    private boolean finished;

    BenchmarkRun(CommandSourceStack source, BenchmarkProfile profile) {
        this.server = source.getServer();
        this.source = source;
        this.profile = profile;
        this.world = new BenchmarkWorld(server);

        stages.add(new Stage() {
            @Override
            public String name() {
                return "benchmark area";
            }

            @Override
            public boolean run(long deadlineNanos) {
                return world.load(deadlineNanos);
            }

            @Override
            public String progress() {
                return world.progress();
            }
        });
        for (int count : profile.villageCounts()) {
            VillageScenario scenario = new VillageScenario(world, count, profile.villageWarmup(), profile.villageTicks(), false);
            villageScenarios.add(scenario);
            stages.add(Stage.once("bells for " + count + " villages", scenario::prepare));
            stages.add(Stage.idle("points of interest", POI_SETTLE_TICKS));
            stages.add(scenario);
        }
        unloaded = new VillageScenario(world, BUDGET_VILLAGES, profile.villageWarmup(), profile.villageTicks(), true);
        stages.add(unloaded);
        stages.add(memory);
        List<MobScenario.Kind> kinds = new ArrayList<>(List.of(MobScenario.Kind.GUARDS_PATROL, MobScenario.Kind.GUARDS_COMBAT));
        for (int count : profile.guardCounts()) {
            // The vanilla mobs a guard is compared with, at the count the guard budget is for.
            if (count == BUDGET_GUARDS) kinds.addAll(List.of(MobScenario.Kind.VILLAGERS, MobScenario.Kind.IRON_GOLEMS));
            for (MobScenario.Kind kind : kinds) {
                MobScenario scenario = new MobScenario(world, kind, count, profile.guardWarmup(), profile.guardTicks());
                mobScenarios.add(scenario);
                stages.add(scenario);
                stages.add(Stage.idle("settle", GUARD_SETTLE_TICKS));
            }
            kinds.retainAll(List.of(MobScenario.Kind.GUARDS_PATROL, MobScenario.Kind.GUARDS_COMBAT));
        }
        totalStages = stages.size();
    }

    boolean isFinished() {
        return finished;
    }

    void cancel(String reason) {
        if (cancelReason == null) cancelReason = reason;
    }

    String status() {
        Stage stage = stages.peek();
        String where = stage == null ? "writing the report"
                : stage.name() + (stage.progress().isEmpty() ? "" : " (" + stage.progress() + ")");
        int number = Math.min(totalStages, totalStages - stages.size() + 1);
        return String.format(Locale.ROOT, "Civillagers benchmark '%s': stage %d/%d, %s, %.1f s elapsed",
                profile.name(), number, totalStages, where, seconds());
    }

    void tick() {
        if (finished) return;
        if (cancelReason != null) {
            finish("cancelled", cancelReason, null);
            return;
        }
        long deadline = System.nanoTime() + TICK_BUDGET_NANOS;
        try {
            while (!stages.isEmpty()) {
                Stage stage = stages.peek();
                if (!stage.run(deadline)) return;
                stages.poll();
                logFinished(stage);
                // A real-tick stage must start on a tick of its own, so the next one waits for the next tick.
                if (!stages.isEmpty() && (stages.peek() instanceof MobScenario || System.nanoTime() >= deadline)) return;
            }
        } catch (Throwable error) {
            finish("failed", error.toString(), error);
            return;
        }
        finish("ok", null, null);
    }

    private void logFinished(Stage stage) {
        String detail = "";
        if (stage instanceof VillageScenario scenario && scenario.result() != null) {
            JsonObject result = scenario.result();
            detail = String.format(Locale.ROOT, ": %s ms/tick mean, %s ms max, %s us/update",
                    result.getAsJsonObject("msPerTick").get("mean"), result.getAsJsonObject("msPerTick").get("max"),
                    result.get("microsPerUpdate"));
        } else if (stage instanceof MobScenario scenario && scenario.result() != null) {
            JsonObject result = scenario.result();
            detail = String.format(Locale.ROOT, ": %s ms/tick mean, %s ms max, %s us/mob/tick%s",
                    result.getAsJsonObject("msPerTick").get("mean"), result.getAsJsonObject("msPerTick").get("max"),
                    result.get("microsPerMobPerTick"),
                    result.has("scansPerGuardPer10Ticks") ? ", " + result.get("scansPerGuardPer10Ticks") + " scans/guard/10 ticks" : "");
        }
        BenchmarkRunner.LOGGER.info("[CivBenchmark] finished {} at {} s{}", stage.name(),
                String.format(Locale.ROOT, "%.1f", seconds()), detail);
    }

    private void finish(String status, String message, Throwable error) {
        finished = true;
        if (error != null) BenchmarkRunner.LOGGER.error("[CivBenchmark] the run failed", error);
        try {
            world.close();
        } catch (Throwable closeError) {
            BenchmarkRunner.LOGGER.error("[CivBenchmark] could not restore the benchmark area", closeError);
        }
        JsonObject report = report(status, message);
        Path file = BenchmarkReport.write(report);
        List<String> lines = BenchmarkReport.format(report, file);
        for (String line : lines) BenchmarkRunner.LOGGER.info("[CivBenchmark] {}", line);
        if (source.isPlayer()) {
            for (String line : lines) source.sendSuccess(() -> Component.literal(line), false);
        }
        BenchmarkRunner.LOGGER.info("[CivBenchmark] DONE status={} in {} s{}", status,
                String.format(Locale.ROOT, "%.1f", seconds()), file != null ? " report=" + file : "");
    }

    private JsonObject report(String status, String message) {
        JsonObject json = new JsonObject();
        json.addProperty("tool", "civillagers benchmark");
        json.addProperty("schema", 1);
        json.addProperty("status", status);
        if (message != null) json.addProperty("message", message);
        json.addProperty("startedAt", startedAt);
        json.addProperty("durationSeconds", Metrics.round(seconds()));
        json.add("profile", profile.toJson());
        json.add("environment", environment());
        JsonObject settings = new JsonObject();
        settings.addProperty("villageInterval", VillageScheduler.INTERVAL);
        settings.addProperty("schedulerBudgetMs", VillageScheduler.budgetNanos() / 1_000_000.0);
        settings.addProperty("schedulerMaxPerTick", VillageScheduler.maxPerTick());
        settings.addProperty("guardPathsPerTick", com.civillagers.ai.GuardPathBudget.PER_TICK);
        json.add("settings", settings);

        JsonArray villages = new JsonArray();
        for (VillageScenario scenario : villageScenarios) if (scenario.result() != null) villages.add(scenario.result());
        json.add("villageScenarios", villages);
        if (unloaded.result() != null) json.add("unloadedVillages", unloaded.result());
        JsonArray mobs = new JsonArray();
        for (MobScenario scenario : mobScenarios) if (scenario.result() != null) mobs.add(scenario.result());
        json.add("mobScenarios", mobs);
        if (memory.result() != null) json.add("memory", memory.result());
        json.addProperty("gcCollectionsDuringRun", Metrics.gcCount() - gcBefore);
        json.addProperty("cachedEntriesAfterRun", Civillagers.cachedEntries());
        json.add("budgets", budgets());
        return json;
    }

    /** Each budget from docs/ROADMAP.md this run has a figure for, with the figure and whether it is met. */
    private JsonArray budgets() {
        JsonArray budgets = new JsonArray();
        for (VillageScenario scenario : villageScenarios) {
            JsonObject result = scenario.result();
            if (result != null && result.get("villages").getAsInt() == BUDGET_VILLAGES) {
                budgets.add(budget("phase 1: 50 empty villages, mean ms/tick",
                        result.getAsJsonObject("msPerTick").get("mean").getAsDouble(), BUDGET_VILLAGES_MS));
            }
        }
        if (unloaded.result() != null) {
            budgets.add(budget("phase 1: 50 villages in unloaded chunks, mean ms/tick",
                    unloaded.result().getAsJsonObject("msPerTick").get("mean").getAsDouble(), BUDGET_UNLOADED_MS));
        }
        if (memory.result() != null) {
            budgets.add(budget("one village's data, retained bytes",
                    memory.result().get("retainedBytesPerVillage").getAsDouble(), BUDGET_VILLAGE_BYTES));
        }
        double worstScans = -1;
        for (MobScenario scenario : mobScenarios) {
            JsonObject result = scenario.result();
            if (result == null || !scenario.kind().guards()) continue;
            worstScans = Math.max(worstScans, result.get("scansPerGuardPer10Ticks").getAsDouble());
            if (result.get("mobs").getAsInt() != BUDGET_GUARDS) continue;
            String what = result.get("combat").getAsBoolean() ? "in combat" : "on patrol";
            budgets.add(budget("phase 2: 20 guards " + what + ", mean ms/tick",
                    result.getAsJsonObject("msPerTick").get("mean").getAsDouble(), BUDGET_GUARDS_MS));
        }
        if (worstScans >= 0) {
            budgets.add(budget("phase 2: target scans per guard per 10 ticks, worst scenario", worstScans, BUDGET_SCANS_PER_10_TICKS));
        }
        return budgets;
    }

    private static JsonObject budget(String name, double value, double limit) {
        JsonObject json = new JsonObject();
        json.addProperty("name", name);
        json.addProperty("value", Metrics.round4(value));
        json.addProperty("limit", limit);
        json.addProperty("met", value <= limit);
        return json;
    }

    private JsonObject environment() {
        JsonObject json = new JsonObject();
        json.addProperty("minecraft", Civillagers.MINECRAFT);
        json.addProperty("loader", DevLoader.LOADER);
        json.addProperty("modVersion", DevLoader.modVersion(Civillagers.MOD_ID));
        json.addProperty("dedicatedServer", server.isDedicatedServer());
        json.addProperty("java", System.getProperty("java.version"));
        json.addProperty("vm", System.getProperty("java.vm.name") + " " + System.getProperty("java.vm.version"));
        json.addProperty("os", System.getProperty("os.name") + " " + System.getProperty("os.version")
                + " (" + System.getProperty("os.arch") + ")");
        String cpu = System.getenv("PROCESSOR_IDENTIFIER");
        json.addProperty("cpu", cpu == null || cpu.isBlank() ? "unknown" : cpu.trim());
        json.addProperty("availableProcessors", Runtime.getRuntime().availableProcessors());
        json.addProperty("maxHeapMiB", Metrics.round(Runtime.getRuntime().maxMemory() / MIB));
        json.addProperty("allocationTracking", Metrics.allocationTracking());
        json.addProperty("profiling", profiling());
        json.addProperty("levelDirectory", server.getWorldPath(LevelResource.ROOT)
                .toAbsolutePath().normalize().getFileName().toString());
        json.addProperty("levelSeed", world.level.getSeed());
        json.addProperty("difficulty", world.level.getDifficulty().name().toLowerCase(Locale.ROOT));
        json.addProperty("benchmarkArea", world.describe());
        return json;
    }

    /** Whether a flight recording, a profiler or a debugger is attached: every figure is then slower than the mod is. */
    private static boolean profiling() {
        for (String argument : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (argument.contains("StartFlightRecording") || argument.startsWith("-agentpath:")
                    || argument.startsWith("-agentlib:jdwp") || argument.equals("-Xint")) {
                return true;
            }
        }
        return false;
    }

    private double seconds() {
        return (System.nanoTime() - startedNanos) / 1e9;
    }
}
