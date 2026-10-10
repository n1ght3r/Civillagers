package com.civillagers.dev.agent.client;

import com.civillagers.dev.agent.core.AgentDispatcher;
import com.civillagers.dev.agent.core.AgentException;
import com.civillagers.dev.agent.core.AgentReply;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * What a client answers: its window and screen, where its player is and looks, a capture of the
 * framebuffer, and commands sent through the player's own connection. Nothing here reads or moves the
 * operating system's mouse or keyboard, so a client answers the same whether its window is in front,
 * behind another or never focused at all.
 */
public final class ClientProbes {
    private static final int CAPTURE_POLL_TICKS = 2;
    private static final int CAPTURE_ATTEMPTS = 30;
    private static int captures;

    private ClientProbes() { }

    public static void register(AgentDispatcher dispatcher) {
        dispatcher.register("client.info", (request, reply) -> {
            Minecraft minecraft = client();
            JsonObject result = new JsonObject();
            result.addProperty("frameWidth", minecraft.getWindow().getWidth());
            result.addProperty("frameHeight", minecraft.getWindow().getHeight());
            result.addProperty("fps", minecraft.getFps());
            result.addProperty("screen", screenName(minecraft));
            result.addProperty("inWorld", minecraft.level != null);
            result.addProperty("singleplayer", minecraft.hasSingleplayerServer());
            // The world -Pquickplay asked for, and whether the client is still outside it: a world that
            // fails to load drops back to a menu without stopping the game.
            String quickplay = System.getProperty("civillagers.agent.quickplay");
            result.addProperty("quickplay", quickplay);
            result.addProperty("quickplayFailed", quickplay != null && minecraft.level == null);
            LocalPlayer player = minecraft.player;
            result.addProperty("player", player == null ? null : player.getScoreboardName());
            result.addProperty("hudHidden", AgentClientVanilla.hudHidden(minecraft));
            // Whether this client is driven, and whether it holds the mouse pointer. A driven one never does.
            result.addProperty("driven", ClientWindow.driven());
            result.addProperty("mouseGrabbed", minecraft.mouseHandler.isMouseGrabbed());
            reply.ok(result);
        });

        dispatcher.register("client.state", (request, reply) -> {
            LocalPlayer player = player(client());
            JsonObject result = new JsonObject();
            result.addProperty("alive", player.isAlive());
            result.addProperty("health", player.getHealth());
            result.addProperty("dimension", player.level().dimension().identifier().toString());
            JsonArray pos = new JsonArray();
            pos.add(player.getX());
            pos.add(player.getY());
            pos.add(player.getZ());
            result.add("pos", pos);
            result.addProperty("yaw", player.getYRot());
            result.addProperty("pitch", player.getXRot());
            reply.ok(result);
        });

        // Turns the player's view, by angles or towards a point, so a capture frames what a script wants.
        dispatcher.register("client.look", (request, reply) -> {
            LocalPlayer player = player(client());
            float yaw;
            float pitch;
            if (request.has("at")) {
                List<JsonElement> at = request.list("at");
                if (at.size() != 3) throw new AgentException("at must be [x, y, z]");
                double dx = at.get(0).getAsDouble() - player.getX();
                double dy = at.get(1).getAsDouble() - player.getEyeY();
                double dz = at.get(2).getAsDouble() - player.getZ();
                yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
                pitch = (float) -(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 180.0 / Math.PI);
            } else {
                yaw = request.decimal("yaw", player.getYRot());
                pitch = request.decimal("pitch", player.getXRot());
            }
            player.setYRot(yaw);
            player.setXRot(pitch);
            player.yRotO = yaw;
            player.xRotO = pitch;
            player.setYHeadRot(yaw);
            JsonObject result = new JsonObject();
            result.addProperty("yaw", yaw);
            result.addProperty("pitch", pitch);
            reply.ok(result);
        });

        dispatcher.register("client.hud.toggle", (request, reply) -> {
            Minecraft minecraft = client();
            AgentClientVanilla.toggleHud(minecraft);
            JsonObject result = new JsonObject();
            result.addProperty("hudHidden", AgentClientVanilla.hudHidden(minecraft));
            reply.ok(result);
        });

        // Writes the framebuffer to a PNG beside the queue. Evidence for a person, never an assertion.
        dispatcher.register("client.capture", (request, reply) -> {
            Minecraft minecraft = client();
            String name = request.string("name", null);
            String file = name == null || name.isBlank() ? String.format("frame-%04d", ++captures) : name.strip();
            if (!file.endsWith(".png")) file = file + ".png";
            Path directory = dispatcher.queue().directory();
            Path target = directory.resolve(Screenshot.SCREENSHOT_DIR).resolve(file);
            AtomicReference<String> message = new AtomicReference<>();
            Files.createDirectories(target.getParent());
            Files.deleteIfExists(target);
            AgentClientVanilla.screenshot(minecraft, directory.toFile(), file, component -> message.set(component.getString()));
            waitForCapture(dispatcher, reply, target, message, CAPTURE_ATTEMPTS);
        });

        // Commands go through the player's own connection, so the server sees them exactly as typed.
        dispatcher.register("client.command", (request, reply) -> {
            String command = request.string("command").strip();
            if (command.startsWith("/")) command = command.substring(1);
            player(client()).connection.sendCommand(command);
            JsonObject result = new JsonObject();
            result.addProperty("command", command);
            reply.ok(result);
        });
    }

    private static void waitForCapture(AgentDispatcher dispatcher, AgentReply reply, Path target,
                                       AtomicReference<String> message, int attemptsLeft) {
        dispatcher.defer(CAPTURE_POLL_TICKS, reply, () -> {
            if (message.get() != null && Files.isRegularFile(target)) {
                JsonObject result = new JsonObject();
                result.addProperty("file", target.toAbsolutePath().toString());
                result.addProperty("message", message.get());
                reply.ok(result);
                return;
            }
            if (attemptsLeft <= 1) {
                reply.fail("the capture did not reach " + target + " within " + CAPTURE_ATTEMPTS * CAPTURE_POLL_TICKS + " ticks");
                return;
            }
            waitForCapture(dispatcher, reply, target, message, attemptsLeft - 1);
        });
    }

    private static String screenName(Minecraft minecraft) {
        Screen screen = AgentClientVanilla.screen(minecraft);
        return screen == null ? null : screen.getClass().getName();
    }

    private static Minecraft client() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) throw new AgentException("there is no client in this process");
        return minecraft;
    }

    private static LocalPlayer player(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null) throw new AgentException("no player on this client; it is at " + screenName(minecraft));
        return player;
    }
}
