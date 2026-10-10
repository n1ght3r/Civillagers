package com.civillagers.dev;

import com.civillagers.command.CivillagersCommands;
import com.civillagers.entity.CivEntities;
import com.civillagers.entity.Guard;
import com.civillagers.platform.Vanilla;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * {@code /civillagers showcase}: a row of guards standing still in front of the player, each in different
 * gear, to look at the guard's model, its held items and its armor in a real client. With
 * {@code -Dcivillagers.showcase=true} it runs by itself shortly after the first player joins, so a client
 * launched straight into a world shows it with nobody typing.
 */
public final class Showcase {
    public static final String PROPERTY = "civillagers.showcase";
    private static final int DELAY_TICKS = 60;
    private static final double DISTANCE = 4.5;
    private static final double SPACING = 1.6;

    /** What each guard in the row wears: main hand, off hand, then head, chest, legs, feet. */
    private static final Item[][] OUTFITS = {
            {Items.IRON_SWORD, null, null, null, null, null},
            {Items.DIAMOND_SWORD, Items.SHIELD, Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS},
            {Items.BOW, null, Items.IRON_HELMET, null, null, null},
            {Items.IRON_AXE, null, null, Items.GOLDEN_CHESTPLATE, Items.LEATHER_LEGGINGS, null},
            {Items.STONE_SWORD, Items.SHIELD, Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, null, Items.IRON_BOOTS},
    };
    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
    };

    private static int waited;
    private static boolean shown;

    private Showcase() { }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(CivillagersCommands.root().then(Commands.literal("showcase")
                .requires(Vanilla::isGamemaster)
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    int spawned = spawn(player);
                    context.getSource().sendSuccess(() -> Component.literal("Spawned " + spawned + " guards to look at"), false);
                    return spawned;
                })));
    }

    /** The automatic showcase, for a client launched with {@link #PROPERTY}. */
    static void tick(MinecraftServer server) {
        if (shown || !Boolean.getBoolean(PROPERTY)) return;
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty() || ++waited < DELAY_TICKS) return;
        shown = true;
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "time set noon");
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "weather clear");
        spawn(players.get(0));
    }

    /** Spawns the row in front of {@code player}, facing them, and returns how many guards it placed. */
    static int spawn(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 look = player.getLookAngle();
        double length = Math.sqrt(look.x * look.x + look.z * look.z);
        double fx = length < 1.0E-4 ? 0 : look.x / length;
        double fz = length < 1.0E-4 ? 1 : look.z / length;
        int spawned = 0;
        for (int i = 0; i < OUTFITS.length; i++) {
            double side = (i - (OUTFITS.length - 1) / 2.0) * SPACING;
            double x = player.getX() + fx * DISTANCE - fz * side;
            double z = player.getZ() + fz * DISTANCE + fx * side;
            int bx = (int) Math.floor(x);
            int bz = (int) Math.floor(z);
            BlockPos at = new BlockPos(bx, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz), bz);
            Guard guard = CivEntities.guard().spawn(level, at, EntitySpawnReason.MOB_SUMMONED);
            if (guard == null) continue;
            guard.setNoAi(true);
            for (int s = 0; s < SLOTS.length; s++) {
                Item item = OUTFITS[i][s];
                guard.setItemSlot(SLOTS[s], item == null ? ItemStack.EMPTY : new ItemStack(item));
            }
            // A guard with a bow draws it when it fights; show that pose.
            if (guard.holdsBow()) guard.setAggressive(true);
            float yaw = (float) (Math.atan2(player.getZ() - guard.getZ(), player.getX() - guard.getX()) * 180.0 / Math.PI) - 90.0F;
            guard.setYRot(yaw);
            guard.setYHeadRot(yaw);
            guard.yBodyRot = yaw;
            spawned++;
        }
        return spawned;
    }
}
