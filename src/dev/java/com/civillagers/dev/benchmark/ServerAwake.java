package com.civillagers.dev.benchmark;

import net.minecraft.server.MinecraftServer;

import java.lang.reflect.Field;

/**
 * Keeps a dedicated server with nobody online from pausing while a benchmark runs or the agent's queue
 * is open. From 1.21.2 a dedicated server stops ticking once it has been empty for
 * {@code pause-when-empty-seconds}, and a paused server fires no tick event, so a run would never advance
 * and a queue would stop answering. There is no public way to hold the pause off; the field is the Mojang
 * name every version that has the pause runs with in development, and a version without it loses nothing.
 */
public final class ServerAwake {
    private static final Field EMPTY_TICKS = emptyTicksField();
    private static boolean warned;

    private ServerAwake() { }

    public static void keep(MinecraftServer server) {
        if (EMPTY_TICKS == null || server == null || !server.isDedicatedServer()) return;
        try {
            EMPTY_TICKS.setInt(server, 0);
        } catch (IllegalAccessException | RuntimeException e) {
            if (!warned) {
                warned = true;
                BenchmarkRunner.LOGGER.warn("[CivBenchmark] could not keep the empty server from pausing", e);
            }
        }
    }

    private static Field emptyTicksField() {
        try {
            Field field = MinecraftServer.class.getDeclaredField("emptyTicks");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException e) {
            // Before 1.21.2 there is no pause, and nothing to hold off.
            return null;
        }
    }
}
