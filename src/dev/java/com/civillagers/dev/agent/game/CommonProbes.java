package com.civillagers.dev.agent.game;

import com.civillagers.Civillagers;
import com.civillagers.dev.agent.core.AgentDispatcher;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.List;

/** The commands every process answers, whichever side it is. */
final class CommonProbes {
    private CommonProbes() { }

    static void register(AgentDispatcher dispatcher, String side) {
        // The heartbeat: what this is, and the one answer that never depends on a world being there.
        dispatcher.register("probe", (request, reply) -> {
            JsonObject result = CivAgent.describe();
            result.addProperty("queue", dispatcher.queue().directory().toAbsolutePath().toString());
            result.addProperty("serverRunning", CivAgent.server() != null);
            result.addProperty("pending", dispatcher.pending());
            JsonArray commands = new JsonArray();
            dispatcher.commands().forEach(commands::add);
            result.add("commands", commands);
            reply.ok(result);
        });

        // Answers once that many game ticks have run: how a script lets the game catch up without
        // guessing at wall-clock sleeps.
        dispatcher.register("wait", (request, reply) -> {
            int ticks = request.integer("ticks", 1, 20 * 60);
            dispatcher.defer(ticks, reply, () -> {
                JsonObject result = new JsonObject();
                result.addProperty("ticks", ticks);
                reply.ok(result);
            });
        });

        // Live instances per class after a full collection, the JVM's own class histogram (what
        // `jcmd <pid> GC.class_histogram` prints), for the leak check: `classes` are counted by exact name,
        // even at zero, in the order given (a list, since a script's dotted paths cannot name a class), and
        // `prefixes` list every class under them that has instances. What lives as long as the mod does is
        // left out: lambdas, hidden classes, enum constants, and the classes named in `ignore` (singletons).
        dispatcher.register("jvm.histogram", (request, reply) -> {
            List<String> classes = request.has("classes") ? request.strings("classes") : List.of();
            List<String> prefixes = request.has("prefixes") ? request.strings("prefixes") : List.of();
            List<String> ignore = request.has("ignore") ? request.strings("ignore") : List.of();
            String histogram = (String) ManagementFactory.getPlatformMBeanServer().invoke(
                    new ObjectName("com.sun.management:type=DiagnosticCommand"), "gcClassHistogram",
                    new Object[] {null}, new String[] {String[].class.getName()});
            long[] counts = new long[classes.size()];
            JsonObject matched = new JsonObject();
            long total = 0;
            // Each row: "   12:          345        67890  com.example.Name (module)".
            for (String line : histogram.split("\\R")) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length < 4 || !parts[0].endsWith(":")) continue;
                String name = parts[3];
                long instances = Long.parseLong(parts[1]);
                int index = classes.indexOf(name);
                if (index >= 0) counts[index] = instances;
                if (name.contains("$$Lambda") || name.contains("/0x") || ignore.contains(name)) continue;
                for (String prefix : prefixes) {
                    if (!name.startsWith(prefix) || isEnum(name)) continue;
                    matched.addProperty(name, instances);
                    total += instances;
                    break;
                }
            }
            JsonArray counted = new JsonArray();
            for (int i = 0; i < counts.length; i++) {
                JsonObject row = new JsonObject();
                row.addProperty("name", classes.get(i));
                row.addProperty("instances", counts[i]);
                counted.add(row);
            }
            JsonObject result = new JsonObject();
            result.add("classes", counted);
            result.add("matched", matched);
            result.addProperty("matchedInstances", total);
            reply.ok(result);
        });

        // Stops this process. The answer is written first, so out.jsonl is complete afterwards.
        dispatcher.register("stop", (request, reply) -> {
            reply.ok();
            Civillagers.LOGGER.info("[CivAgent] stopping, as the queue asked");
            CivAgent.stop();
        });
    }

    /** Whether {@code name} is an enum, or one of its constants with a body of its own. */
    private static boolean isEnum(String name) {
        try {
            return Enum.class.isAssignableFrom(Class.forName(name, false, CommonProbes.class.getClassLoader()));
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }
}
