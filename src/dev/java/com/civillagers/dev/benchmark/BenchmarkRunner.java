package com.civillagers.dev.benchmark;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Owns the single benchmark run a server can have at a time. Server thread only. */
public final class BenchmarkRunner {
    public static final Logger LOGGER = LoggerFactory.getLogger("civillagers-benchmark");

    private static BenchmarkRun current;
    private static boolean exitWhenDone;

    private BenchmarkRunner() { }

    static boolean start(CommandSourceStack source, BenchmarkProfile profile) {
        if (current != null) return false;
        ServerAwake.keep(source.getServer());
        current = new BenchmarkRun(source, profile);
        LOGGER.info("[CivBenchmark] started profile '{}': {}", profile.name(), profile.describe());
        return true;
    }

    public static boolean isRunning() {
        return current != null;
    }

    static String status() {
        BenchmarkRun run = current;
        return run == null ? "idle" : run.status();
    }

    static boolean cancel() {
        BenchmarkRun run = current;
        if (run == null) return false;
        run.cancel("cancelled by command");
        return true;
    }

    /** Stops the server once the active run has finished, whatever its outcome. Used by runBenchmark. */
    public static void exitWhenDone(boolean exit) {
        exitWhenDone = exit;
    }

    /** Advances the active run, at the end of every server tick. */
    public static void tick(MinecraftServer server) {
        BenchmarkRun run = current;
        if (run == null) return;
        ServerAwake.keep(server);
        run.tick();
        if (!run.isFinished()) return;
        current = null;
        if (exitWhenDone) {
            exitWhenDone = false;
            LOGGER.info("[CivBenchmark] stopping the server as requested");
            server.halt(false);
        }
    }

    /** Ends an active run while the server shuts down, so the benchmark area is still restored. */
    public static void stop(MinecraftServer server) {
        BenchmarkRun run = current;
        if (run == null) return;
        run.cancel("server stopping");
        run.tick();
        current = null;
    }
}
