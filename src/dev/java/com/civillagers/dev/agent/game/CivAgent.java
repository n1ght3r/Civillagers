package com.civillagers.dev.agent.game;

import com.civillagers.Civillagers;
import com.civillagers.dev.agent.core.AgentDispatcher;
import com.civillagers.dev.agent.core.AgentQueue;
import com.civillagers.dev.agent.core.AgentWatchdog;
import com.civillagers.dev.benchmark.ServerAwake;
import com.civillagers.dev.platform.DevLoader;
import com.civillagers.platform.Loader;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * The agent, wired up: one queue per game process, polled on that process's own tick.
 *
 * <p>A dedicated server and a client of the same node share {@code run/<node>}, so the queue directory
 * carries a name, {@code server} or {@code client} by default and {@code -Dcivillagers.agent=<name>}
 * otherwise, and the two never write to one file: {@code run/<node>/agent/<name>/}.
 *
 * <p>One queue per process rather than one per side: a client with an integrated server is one process,
 * and {@code server.*} commands reach its integrated server from the client's queue.
 */
public final class CivAgent {
    /** {@code -Dcivillagers.agent=<name>} names this process's queue directory. */
    public static final String NAME_PROPERTY = "civillagers.agent";
    /**
     * {@code -Dcivillagers.agent.script=<file>} answers a file of requests once, at startup, and
     * {@code -Dcivillagers.agent.script.exit=true} stops the game once it has been answered. {@code -Pagent}
     * sets both.
     */
    public static final String SCRIPT_PROPERTY = "civillagers.agent.script";
    /** Seconds an unattended run may go without answering before it stops: 300 by default. */
    public static final String STALL_PROPERTY = "civillagers.agent.stallSeconds";
    /** Seconds an unattended run's game thread may go without a tick before the process is halted. */
    public static final String FROZEN_PROPERTY = "civillagers.agent.frozenSeconds";

    private static AgentDispatcher dispatcher;
    private static String side;
    private static Runnable exit;
    private static MinecraftServer server;

    private CivAgent() { }

    /**
     * Builds this process's queue and registers what it answers. {@code clientProbes} registers the
     * {@code client.*} commands in a game with a window, and is null on a dedicated server; it is handed in
     * so this class never names a client class. {@code exit} is how the process stops itself.
     */
    public static void install(Consumer<AgentDispatcher> clientProbes, Runnable exit) {
        boolean client = clientProbes != null;
        if (dispatcher != null) return;
        side = client ? "client" : "server";
        CivAgent.exit = exit;
        Path directory = DevLoader.gameDir().resolve("agent").resolve(queueName());
        dispatcher = new AgentDispatcher(new AgentQueue(directory, Civillagers.LOGGER), Civillagers.LOGGER, describe());
        CommonProbes.register(dispatcher, side);
        ServerProbes.register(dispatcher);
        if (client) clientProbes.accept(dispatcher);
        Loader.onServerStarted(started -> server = started);
        DevLoader.onServerStopping(stopping -> server = null);
    }

    /**
     * Installs the agent on a dedicated server: the queue opens once the server has started, is polled on
     * the server tick, and {@code stop} halts the server.
     */
    public static void installServer() {
        install(null, () -> {
            MinecraftServer running = server;
            if (running != null) running.halt(false);
        });
        Loader.onServerStarted(started -> start());
        Loader.onServerTickEnd(CivAgent::serverTick);
        DevLoader.onServerStopping(stopping -> flush());
    }

    /** What this process is: side, loader, Minecraft, the mod's version and where it runs. */
    static JsonObject describe() {
        JsonObject about = new JsonObject();
        about.addProperty("side", side);
        about.addProperty("name", queueName());
        about.addProperty("loader", DevLoader.LOADER);
        about.addProperty("minecraft", Civillagers.MINECRAFT);
        about.addProperty("modVersion", DevLoader.modVersion(Civillagers.MOD_ID));
        about.addProperty("runDirectory", DevLoader.gameDir().toAbsolutePath().toString());
        return about;
    }

    /** The running server, integrated or dedicated, or null when there is none. */
    static MinecraftServer server() {
        return server;
    }

    /** Opens the queue and runs the launch's script, if it named one. Called once the process can answer. */
    public static void start() {
        if (dispatcher == null) return;
        boolean open = dispatcher.start();
        String script = System.getProperty(SCRIPT_PROPERTY);
        if (script == null || script.isBlank()) return;
        boolean stopWhenDone = Boolean.getBoolean(SCRIPT_PROPERTY + ".exit");
        if (!open) {
            // Most likely another game of this node still running; running the script anyway would mix
            // both runs' answers.
            Civillagers.LOGGER.error("[CivAgent] DONE refused, the script was not run: the queue in {} is not "
                    + "this game's; the line above says why", dispatcher.queue().directory().toAbsolutePath());
            if (stopWhenDone) stop();
            return;
        }
        if (stopWhenDone) {
            // Nobody is watching an unattended run, so it must end on its own even when it cannot finish.
            int stall = Integer.getInteger(STALL_PROPERTY, 300);
            dispatcher.watchStall(stall, () -> {
                Civillagers.LOGGER.error("[CivAgent] DONE stalled: no request answered for {} s while {} waited; stopping",
                        stall, dispatcher.status());
                dispatcher.stop("the run stalled, and was stopped before this request was started");
                if (exit != null) exit.run();
            });
            AgentWatchdog.start(dispatcher, Thread.currentThread(), Integer.getInteger(FROZEN_PROPERTY, 300), Civillagers.LOGGER);
        }
        dispatcher.runScript(Path.of(script.trim()), () -> {
            Civillagers.LOGGER.info("[CivAgent] DONE script answered, out={}",
                    dispatcher.queue().file(AgentQueue.OUT).toAbsolutePath());
            if (stopWhenDone) stop();
        });
    }

    /** Answers whatever is still deferred and closes the queue, without stopping the game. */
    public static void flush() {
        if (dispatcher != null) dispatcher.stop();
    }

    /** What the queue is doing, in one line for a person, or null when the agent is not installed. */
    public static String status() {
        return dispatcher == null ? null : dispatcher.status();
    }

    /** Closes the queue and then stops this process: halting a dedicated server, or closing a window. */
    public static void stop() {
        flush();
        if (exit != null) exit.run();
    }

    /** Called once per tick on a client. */
    public static void tick() {
        if (dispatcher != null) dispatcher.tick();
    }

    /** Called once per tick on a dedicated server, which must also be kept from pausing while empty. */
    public static void serverTick(MinecraftServer running) {
        if (dispatcher == null) return;
        ServerAwake.keep(running);
        dispatcher.tick();
    }

    private static String queueName() {
        String name = System.getProperty(NAME_PROPERTY);
        return name == null || name.isBlank() ? side : name.trim();
    }
}
