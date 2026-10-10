package com.civillagers.dev;

import com.civillagers.command.CivillagersCommands;
import com.civillagers.dev.platform.DevVanilla;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * {@code /civillagers showcase}: a row of guards standing still in front of the player, each in different
 * gear and the clothes of a different villager type, to look at the guard's model, its clothes, its held
 * items, its armor and its poses in a real client: drawing a bow, raising a shield, aiming a loaded
 * crossbow. With
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
            {Items.CROSSBOW, null, Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, null, null},
            {Items.WOODEN_SWORD, null, null, null, null, null},
    };
    /** The villager type each guard in the row wears, one of each of vanilla's. */
    private static final String[] VILLAGER_TYPES = {
            "minecraft:plains", "minecraft:taiga", "minecraft:desert", "minecraft:savanna",
            "minecraft:snow", "minecraft:swamp", "minecraft:jungle",
    };
    /** The guard in the row that raises its shield. */
    private static final int BLOCKING = 4;
    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
    };

    /** Ticks after spawning before the shield goes up; see {@link #tick}. */
    private static final int RAISE_DELAY = 20;

    private static int waited;
    private static boolean shown;
    /** The guard that raises its shield once {@link #raiseIn} has run out, by UUID, never the entity. */
    private static UUID raising;
    private static int raiseIn;

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
        // The shield goes up a second after the row appears: a guard already using an item when a client
        // first sees it reaches that client, on 1.21.1, before its equipment does, and the client drops the use.
        if (raising != null && --raiseIn <= 0) {
            for (ServerLevel level : server.getAllLevels()) {
                if (level.getEntity(raising) instanceof Guard guard) guard.startUsingItem(InteractionHand.OFF_HAND);
            }
            raising = null;
        }
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
            // Dressed before it joins the level: a client in range is sent the guard as it joins, and on 1.21.1
            // a change made in that same tick, such as taking away the shield a new guard may roll, never
            // reaches it.
            Item[] outfit = OUTFITS[i];
            String type = VILLAGER_TYPES[i];
            Guard guard = CivEntities.guard().spawn(level, created -> dress(created, outfit, type), at,
                    EntitySpawnReason.MOB_SUMMONED, false, false);
            if (guard == null) continue;
            if (i == BLOCKING) {
                raising = guard.getUUID();
                raiseIn = RAISE_DELAY;
            }
            float yaw = (float) (Math.atan2(player.getZ() - guard.getZ(), player.getX() - guard.getX()) * 180.0 / Math.PI) - 90.0F;
            guard.setYRot(yaw);
            guard.setYHeadRot(yaw);
            guard.yBodyRot = yaw;
            spawned++;
        }
        return spawned;
    }

    /** Puts {@code outfit} and the clothes of {@code type} on {@code guard}, still, in the pose its weapon shows best. */
    private static void dress(Guard guard, Item[] outfit, String type) {
        guard.setNoAi(true);
        guard.setVillagerType(type);
        for (int s = 0; s < SLOTS.length; s++) {
            Item item = outfit[s];
            guard.setItemSlot(SLOTS[s], item == null ? ItemStack.EMPTY : new ItemStack(item));
        }
        // A guard with a bow draws it when it fights, and one with a crossbow loads it; show those poses.
        if (guard.holdsBow()) guard.setAggressive(true);
        if (guard.holdsCrossbow()) guard.setItemSlot(EquipmentSlot.MAINHAND, DevVanilla.loadedCrossbow());
    }
}
