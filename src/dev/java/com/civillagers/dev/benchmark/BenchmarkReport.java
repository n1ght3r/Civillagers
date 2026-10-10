package com.civillagers.dev.benchmark;

import com.civillagers.dev.platform.DevLoader;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Writes a finished run's report to disk and renders the same data as console lines. */
final class BenchmarkReport {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);
    private static final String VILLAGE_ROW = "%9s %10s %10s %10s %10s %12s";
    private static final String MOB_ROW = "%-20s %5s %10s %10s %10s %10s %10s";

    private BenchmarkReport() { }

    /**
     * Writes the report to {@code <run directory>/benchmark/civillagers-benchmark-<timestamp>.json} and copies
     * it to {@code latest.json} next to it. Returns the timestamped file, or null when writing failed.
     */
    static Path write(JsonObject report) {
        try {
            Path directory = DevLoader.gameDir().resolve("benchmark");
            Files.createDirectories(directory);
            String json = GSON.toJson(report);
            Path file = directory.resolve("civillagers-benchmark-" + LocalDateTime.now().format(STAMP) + ".json");
            Files.writeString(file, json, StandardCharsets.UTF_8);
            Files.writeString(directory.resolve("latest.json"), json, StandardCharsets.UTF_8);
            return file.toAbsolutePath();
        } catch (IOException | RuntimeException e) {
            BenchmarkRunner.LOGGER.error("[CivBenchmark] could not write the report", e);
            return null;
        }
    }

    static List<String> format(JsonObject report, Path file) {
        List<String> lines = new ArrayList<>();
        JsonObject environment = object(report, "environment");
        lines.add("Minecraft " + text(environment, "minecraft") + " on " + text(environment, "loader")
                + ", mod " + text(environment, "modVersion") + ", Java " + text(environment, "java") + ", "
                + text(environment, "availableProcessors") + " CPUs, profile " + text(object(report, "profile"), "name"));
        lines.add("Area: " + text(environment, "benchmarkArea"));

        lines.add("Villages, per simulated tick (a tick has 50 ms):");
        lines.add(String.format(Locale.ROOT, VILLAGE_ROW, "villages", "mean ms", "p99 ms", "max ms", "us/update", "bytes/tick"));
        List<JsonObject> villageRows = new ArrayList<>();
        for (JsonElement element : array(report, "villageScenarios")) villageRows.add(element.getAsJsonObject());
        if (report.has("unloadedVillages")) villageRows.add(object(report, "unloadedVillages"));
        for (JsonObject row : villageRows) {
            JsonObject ms = object(row, "msPerTick");
            String label = text(row, "villages") + (row.get("unloaded").getAsBoolean() ? " far" : "");
            lines.add(String.format(Locale.ROOT, VILLAGE_ROW, label, text(ms, "mean"), text(ms, "p99"),
                    text(ms, "max"), text(row, "microsPerUpdate"), text(row, "allocatedBytesPerTick")));
        }

        lines.add("Mobs, every one's whole tick summed per server tick, guards beside vanilla mobs:");
        lines.add(String.format(Locale.ROOT, MOB_ROW, "kind", "count", "mean ms", "p99 ms", "max ms", "us/mob", "scans/10t"));
        for (JsonElement element : array(report, "mobScenarios")) {
            JsonObject row = element.getAsJsonObject();
            JsonObject ms = object(row, "msPerTick");
            lines.add(String.format(Locale.ROOT, MOB_ROW, text(row, "kind"), text(row, "mobs"), text(ms, "mean"),
                    text(ms, "p99"), text(ms, "max"), text(row, "microsPerMobPerTick"), text(row, "scansPerGuardPer10Ticks")));
        }

        JsonObject memory = object(report, "memory");
        lines.add("Memory: " + text(memory, "retainedBytesPerVillage") + " B retained per typical village ("
                + text(memory, "typicalVillage") + "), " + text(memory, "savedSnbtCharactersPerVillage")
                + " characters saved");

        lines.add("Budgets (docs/ROADMAP.md):");
        for (JsonElement element : array(report, "budgets")) {
            JsonObject budget = element.getAsJsonObject();
            lines.add(String.format(Locale.ROOT, "  [%s] %s: %s (limit %s)",
                    budget.get("met").getAsBoolean() ? "met " : "MISS", text(budget, "name"),
                    text(budget, "value"), text(budget, "limit")));
        }
        if (report.has("message")) lines.add("Message: " + text(report, "message"));
        lines.add("Status " + text(report, "status") + " after " + text(report, "durationSeconds") + " s"
                + (file != null ? ", report " + file : ", report not written"));
        return lines;
    }

    private static JsonObject object(JsonObject parent, String key) {
        return parent.has(key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : new JsonObject();
    }

    private static JsonArray array(JsonObject parent, String key) {
        return parent.has(key) && parent.get(key).isJsonArray() ? parent.getAsJsonArray(key) : new JsonArray();
    }

    private static String text(JsonObject parent, String key) {
        if (!parent.has(key) || parent.get(key).isJsonNull()) return "-";
        JsonElement value = parent.get(key);
        return value.isJsonPrimitive() ? value.getAsString() : value.toString();
    }
}
