package com.civillagers.dev;

import com.civillagers.Civillagers;
import com.civillagers.dev.agent.game.CivAgent;
import com.civillagers.dev.benchmark.BenchmarkCommand;
import com.civillagers.dev.benchmark.BenchmarkRunner;
import com.civillagers.dev.platform.DevLoader;
import com.civillagers.platform.Loader;
import net.minecraft.server.MinecraftServer;

/**
 * What the dev tools mod registers, whichever loader it is: {@code /civillagers benchmark} and the tick
 * that advances a run, {@code /civillagers showcase}, and on a dedicated server the agent's queue (a
 * client's is installed by {@code agent.client.CivillagersDevClient}). Nothing is registered outside a
 * development run, and this source set never reaches a published jar either way.
 */
public final class CivillagersDev {
    /** NeoForge mod ids cannot contain a hyphen, so this is not the Fabric mod's {@code civillagers-dev}. */
    public static final String NEOFORGE_MOD_ID = "civillagers_dev";
    /**
     * {@code -Dcivillagers.benchmark=<arguments>} runs {@code /civillagers benchmark <arguments>} once the
     * server is up, and {@code -Dcivillagers.benchmark.exit=true} stops the server when that run is over,
     * however it ended. {@code runBenchmark} sets both.
     */
    public static final String BENCHMARK_PROPERTY = "civillagers.benchmark";

    private CivillagersDev() { }

    /** Whether the dev tools register anything: in a development run, or with {@code -Dcivillagers.dev=true}. */
    public static boolean enabled() {
        if (Loader.isDevelopmentEnvironment() || Boolean.getBoolean("civillagers.dev")) return true;
        Civillagers.LOGGER.warn("The Civillagers dev tools are loaded outside a development run and stay off");
        return false;
    }

    public static void initialize() {
        if (!enabled()) return;
        // A client installs the agent from its own entrypoint, which may name client classes.
        if (DevLoader.isDedicatedServer()) CivAgent.installServer();
        Loader.onRegisterCommands(BenchmarkCommand::register);
        Loader.onRegisterCommands(Showcase::register);
        Loader.onServerTickEnd(BenchmarkRunner::tick);
        Loader.onServerTickEnd(Showcase::tick);
        DevLoader.onServerStopping(BenchmarkRunner::stop);
        Loader.onServerStarted(CivillagersDev::autorun);
    }

    /** Runs the benchmark the launch asked for, as if it had been typed into the server console. */
    private static void autorun(MinecraftServer server) {
        String argument = System.getProperty(BENCHMARK_PROPERTY);
        if (argument == null) return;
        boolean exit = Boolean.getBoolean(BENCHMARK_PROPERTY + ".exit");
        String command = ("civillagers benchmark " + argument.trim()).trim();
        BenchmarkRunner.LOGGER.info("[CivBenchmark] autorun: /{}{}", command, exit ? " (the server stops when it is done)" : "");
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
        if (BenchmarkRunner.isRunning()) {
            BenchmarkRunner.exitWhenDone(exit);
            return;
        }
        // A typo in -Pbenchmark would otherwise leave an unattended server running forever.
        BenchmarkRunner.LOGGER.error("[CivBenchmark] DONE status=failed: '/{}' did not start a benchmark", command);
        if (exit) server.halt(false);
    }
}
