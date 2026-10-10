package com.civillagers.dev.agent.game;

import com.civillagers.Civillagers;
import com.civillagers.dev.agent.core.AgentDispatcher;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** The three commands every process answers, whichever side it is. */
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

        // Stops this process. The answer is written first, so out.jsonl is complete afterwards.
        dispatcher.register("stop", (request, reply) -> {
            reply.ok();
            Civillagers.LOGGER.info("[CivAgent] stopping, as the queue asked");
            CivAgent.stop();
        });
    }
}
