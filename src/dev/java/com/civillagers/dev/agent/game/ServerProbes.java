package com.civillagers.dev.agent.game;

import com.civillagers.dev.agent.core.AgentDispatcher;
import com.civillagers.dev.agent.core.AgentException;
import com.civillagers.dev.agent.core.AgentRequest;
import com.civillagers.entity.CivEntities;
import com.civillagers.entity.Guard;
import com.civillagers.village.Village;
import com.civillagers.village.Villages;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * What a process with a server answers about it, integrated or dedicated: the server itself, the guards,
 * the villages, and running a command with its feedback collected rather than scraped from the log. Every
 * read happens on the server's own thread.
 */
final class ServerProbes {
    private ServerProbes() { }

    static void register(AgentDispatcher dispatcher) {
        dispatcher.register("server.info", (request, reply) -> {
            MinecraftServer server = server();
            reply.ok(onServer(server, "server.info", () -> {
                JsonObject result = new JsonObject();
                result.addProperty("dedicated", server.isDedicatedServer());
                result.addProperty("tickCount", server.getTickCount());
                result.addProperty("players", server.getPlayerCount());
                result.addProperty("difficulty", server.getWorldData().getDifficulty().name());
                return result;
            }));
        });

        dispatcher.register("server.command", (request, reply) -> {
            MinecraftServer server = server();
            String command = request.string("command").strip();
            if (command.startsWith("/")) command = command.substring(1);
            String run = command;
            JsonObject result = onServer(server, "server.command", () -> {
                ServerPlayer as = request.has("as") ? player(server, request) : null;
                Feedback feedback = new Feedback();
                CommandSourceStack source = server.createCommandSourceStack().withSource(feedback);
                if (as != null) {
                    source = source.withEntity(as).withPosition(as.position());
                    if (as.level() instanceof ServerLevel level) source = source.withLevel(level);
                }
                server.getCommands().performPrefixedCommand(source, run);
                JsonObject answer = new JsonObject();
                answer.addProperty("command", run);
                JsonArray messages = new JsonArray();
                feedback.messages.forEach(messages::add);
                answer.add("messages", messages);
                return answer;
            });
            reply.ok(result);
        });

        // Every guard in every level: where it is, what it holds and wears, what it is doing.
        dispatcher.register("server.guards", (request, reply) -> {
            MinecraftServer server = server();
            reply.ok(onServer(server, "server.guards", () -> {
                JsonArray guards = new JsonArray();
                for (ServerLevel level : server.getAllLevels()) {
                    for (Guard guard : level.getEntities(CivEntities.guard(), guard -> true)) guards.add(guard(guard));
                }
                JsonObject result = new JsonObject();
                result.addProperty("count", guards.size());
                result.add("guards", guards);
                return result;
            }));
        });

        // Every village in every level, as /civillagers debug here reports one.
        dispatcher.register("server.villages", (request, reply) -> {
            MinecraftServer server = server();
            reply.ok(onServer(server, "server.villages", () -> {
                JsonArray list = new JsonArray();
                for (ServerLevel level : server.getAllLevels()) {
                    for (Village village : Villages.get(level).sortedVillages()) {
                        JsonObject json = new JsonObject();
                        json.addProperty("id", village.id());
                        json.addProperty("level", level.dimension().identifier().toString());
                        json.add("center", pos(village.center()));
                        json.addProperty("radius", village.radius());
                        json.addProperty("bells", village.anchorCount());
                        json.addProperty("members", village.memberCount());
                        json.addProperty("guards", village.guardCount());
                        json.addProperty("beds", village.beds());
                        json.addProperty("workstations", village.workstations());
                        list.add(json);
                    }
                }
                JsonObject result = new JsonObject();
                result.addProperty("count", list.size());
                result.add("villages", list);
                return result;
            }));
        });
    }

    private static JsonObject guard(Guard guard) {
        JsonObject json = new JsonObject();
        json.addProperty("uuid", guard.getUUID().toString());
        json.add("pos", pos(guard.blockPosition()));
        json.addProperty("health", guard.getHealth());
        json.addProperty("maxHealth", guard.getMaxHealth());
        json.addProperty("sleeping", guard.isSleeping());
        json.addProperty("aggressive", guard.isAggressive());
        json.addProperty("noAi", guard.isNoAi());
        LivingEntity target = guard.getTarget();
        json.addProperty("target", target == null ? null : BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString());
        JsonObject gear = new JsonObject();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = guard.getItemBySlot(slot);
            if (!stack.isEmpty()) gear.addProperty(slot.getName(), BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        }
        json.add("gear", gear);
        return json;
    }

    private static JsonArray pos(BlockPos pos) {
        JsonArray json = new JsonArray();
        json.add(pos.getX());
        json.add(pos.getY());
        json.add(pos.getZ());
        return json;
    }

    private static MinecraftServer server() {
        MinecraftServer server = CivAgent.server();
        if (server == null) throw new AgentException("no server is running in this process; open a world first");
        return server;
    }

    /**
     * The player {@code as} names, or with {@code "@only"} the one player online, whose name a dev client
     * makes up at random. The command still runs with the console's permission, so a script needs no
     * operator rights in its world.
     */
    private static ServerPlayer player(MinecraftServer server, AgentRequest request) {
        String name = request.string("as");
        if (name.equals("@only")) {
            List<ServerPlayer> online = server.getPlayerList().getPlayers();
            if (online.size() != 1) throw new AgentException("\"@only\" needs exactly one player online, not " + online.size());
            return online.get(0);
        }
        ServerPlayer player = server.getPlayerList().getPlayerByName(name);
        if (player == null) throw new AgentException("no player called " + name + " is online");
        return player;
    }

    /** Runs {@code work} on the server's own thread and waits for its answer. */
    private static JsonObject onServer(MinecraftServer server, String what, Supplier<JsonObject> work) {
        if (server.isSameThread()) return work.get();
        try {
            return server.submit(work).get(30, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new AgentException(what + ": the server did not answer within 30 seconds; is the world paused?");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof AgentException refused) throw refused;
            throw new AgentException(what + " failed: " + e.getCause());
        } catch (InterruptedException e) {
            throw new AgentException(what + " was interrupted");
        }
    }

    /** Collects what a command says, rather than letting it go to the log. */
    private static final class Feedback implements CommandSource {
        private final List<String> messages = new ArrayList<>();

        @Override
        public void sendSystemMessage(Component message) {
            messages.add(message.getString());
        }

        @Override
        public boolean acceptsSuccess() {
            return true;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }
    }
}
