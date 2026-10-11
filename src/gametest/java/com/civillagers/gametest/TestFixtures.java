package com.civillagers.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** What several tests need, kept the same on every Minecraft version. */
final class TestFixtures {
    private TestFixtures() { }

    /**
     * A save held in memory, for reading back what a villager of the mod saved without the level's save
     * format, which differs between versions. A value with a codec is kept as it is, not encoded.
     */
    static final class MemorySave implements com.civillagers.platform.SavingMob.Output, com.civillagers.platform.SavingMob.Input {
        private final java.util.Map<String, Object> values = new java.util.HashMap<>();

        @Override
        public void putString(String key, String value) {
            values.put(key, value);
        }

        @Override
        public void putInt(String key, int value) {
            values.put(key, value);
        }

        @Override
        public void putLong(String key, long value) {
            values.put(key, value);
        }

        @Override
        public <T> void store(String key, com.mojang.serialization.Codec<T> codec, T value) {
            values.put(key, value);
        }

        @Override
        public String getString(String key, String fallback) {
            return values.get(key) instanceof String value ? value : fallback;
        }

        @Override
        public int getInt(String key, int fallback) {
            return values.get(key) instanceof Integer value ? value : fallback;
        }

        @Override
        public long getLong(String key, long fallback) {
            return values.get(key) instanceof Long value ? value : fallback;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> java.util.Optional<T> read(String key, com.mojang.serialization.Codec<T> codec) {
            return java.util.Optional.ofNullable((T) values.get(key));
        }
    }

    /**
     * Asserts with a plain message. {@link GameTestHelper#assertTrue} takes a {@link Component} from
     * 1.21.5 and a string before it, so this keeps the call sites the same on every version.
     */
    static void check(GameTestHelper helper, boolean condition, String message) {
        //? if >=1.21.5 {
        helper.assertTrue(condition, Component.literal(message));
        //?} else
        //helper.assertTrue(condition, message);
    }

    /**
     * A bed block. 26.3 keeps the coloured beds in one collection rather than a field each, so the test
     * takes the first bed the registry has instead of naming one.
     */
    static Block anyBed() {
        for (Block block : BuiltInRegistries.BLOCK) {
            if (block instanceof BedBlock) return block;
        }
        throw new IllegalStateException("No bed block registered");
    }

    /** The zombie's entity type. By 26.2 vanilla's entity types moved out of {@code EntityType} into {@code EntityTypes}. */
    static EntityType<? extends Mob> zombie() {
        //? if >=26.2 {
        return net.minecraft.world.entity.EntityTypes.ZOMBIE;
        //?} else
        //return EntityType.ZOMBIE;
    }

    /** The villager's entity type; see {@link #zombie()}. */
    static EntityType<? extends Mob> villager() {
        //? if >=26.2 {
        return net.minecraft.world.entity.EntityTypes.VILLAGER;
        //?} else
        //return EntityType.VILLAGER;
    }

    /** The creeper's entity type; see {@link #zombie()}. */
    static EntityType<? extends Mob> creeper() {
        //? if >=26.2 {
        return net.minecraft.world.entity.EntityTypes.CREEPER;
        //?} else
        //return EntityType.CREEPER;
    }

    /** The skeleton's entity type; see {@link #zombie()}. */
    static EntityType<? extends Mob> skeleton() {
        //? if >=26.2 {
        return net.minecraft.world.entity.EntityTypes.SKELETON;
        //?} else
        //return EntityType.SKELETON;
    }

    /** The vindicator's entity type; see {@link #zombie()}. */
    static EntityType<? extends Mob> vindicator() {
        //? if >=26.2 {
        return net.minecraft.world.entity.EntityTypes.VINDICATOR;
        //?} else
        //return EntityType.VINDICATOR;
    }

    /** The zombified piglin's entity type; see {@link #zombie()}. */
    static EntityType<? extends Mob> zombifiedPiglin() {
        //? if >=26.2 {
        return net.minecraft.world.entity.EntityTypes.ZOMBIFIED_PIGLIN;
        //?} else
        //return EntityType.ZOMBIFIED_PIGLIN;
    }

    /** A crossbow loaded with one arrow. After 1.21.1, loaded projectiles are built from a list by another name. */
    static ItemStack loadedCrossbow() {
        ItemStack crossbow = new ItemStack(Items.CROSSBOW);
        java.util.List<ItemStack> arrow = java.util.List.of(new ItemStack(Items.ARROW));
        //? if >1.21.1 {
        crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.ofNonEmpty(arrow));
        //?} else
        //crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(arrow));
        return crossbow;
    }

    /** Lays a stone floor across the test's 8x8 patch at {@code y}, so mobs stand at {@code y + 1}. */
    static void floor(GameTestHelper helper, int y) {
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
        }
    }

    /**
     * Runs {@code mobs} through up to {@code ticks} ticks of their own, at once, as the level would tick
     * them, after each calling {@code each} and stopping once {@code done} holds. Returns the ticks it took,
     * or -1 if {@code done} never held. A test has 20 ticks of the server (Fabric API's default, and the
     * NeoForge harness's), too few for a chase, so a test of one ticks the mobs itself. The game time
     * stands still meanwhile, so the guards' path budget is given back every tick.
     */
    static int simulate(int ticks, java.util.function.BooleanSupplier done, Runnable each, Mob... mobs) {
        for (int t = 1; t <= ticks; t++) {
            com.civillagers.ai.GuardPathBudget.clear();
            for (Mob mob : mobs) {
                if (!mob.isAlive()) continue;
                mob.setOldPosAndRot();
                mob.tickCount++;
                mob.tick();
            }
            each.run();
            if (done.getAsBoolean()) return t;
        }
        return -1;
    }

    /**
     * Lays out a pool across the test's patch: stone at {@code y} 0, water {@code depth} deep over the
     * columns {@code x0} to {@code x1} (1 to 6) and {@code z} 1 to 6, and stone level with its top all
     * round it, so a mob on the bank stands at {@code depth + 1}.
     */
    static void pool(GameTestHelper helper, int x0, int x1, int depth) {
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                boolean water = x >= x0 && x <= x1 && z >= 1 && z <= 6;
                for (int y = 1; y <= depth; y++) helper.setBlock(new BlockPos(x, y, z), water ? Blocks.WATER : Blocks.STONE);
            }
        }
    }

    /** Places a bed facing north with its foot at {@code foot}, on stone. Returns the head, where its POI is. */
    static BlockPos placeBed(GameTestHelper helper, BlockPos foot) {
        BlockState bed = anyBed().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        helper.setBlock(foot.below(), Blocks.STONE);
        helper.setBlock(foot.north().below(), Blocks.STONE);
        helper.setBlock(foot.north(), bed.setValue(net.minecraft.world.level.block.BedBlock.PART, BedPart.HEAD));
        helper.setBlock(foot, bed.setValue(net.minecraft.world.level.block.BedBlock.PART, BedPart.FOOT));
        return foot.north();
    }

    /**
     * A mock player in the level, standing where {@code entity} stands: guards count it as a player near.
     * Take it out again with {@link #removePlayer} in the same test, before anything else ticks.
     */
    static net.minecraft.server.level.ServerPlayer playerAt(GameTestHelper helper, net.minecraft.world.entity.Entity entity) {
        net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setPos(entity.getX(), entity.getY(), entity.getZ());
        return player;
    }

    static void removePlayer(GameTestHelper helper, net.minecraft.server.level.ServerPlayer player) {
        helper.getLevel().getServer().getPlayerList().remove(player);
    }

    /** A player using {@code hand} on {@code mob}. By 26.1 an interaction also carries where the mob was hit. */
    static void interact(Mob mob, net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
        //? if >=26.1 {
        mob.interact(player, hand, mob.position());
        //?} else
        //mob.interact(player, hand);
    }

    /**
     * Brings a zombie villager's cure to its last tick, so it finishes on the next one. Vanilla starts a
     * cure at three to five minutes; 26.1 has a setter for what is left, and before it the time is set
     * through the zombie's saved data, which starts the cure over with it.
     */
    static void finishCureSoon(ZombieVillager zombie) {
        //? if >=26.1 {
        zombie.setVillagerConversionTime(1);
        //?} else {
        /*net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        zombie.addAdditionalSaveData(tag);
        tag.putInt("ConversionTime", 1);
        zombie.readAdditionalSaveData(tag);
        *///?}
    }

    /**
     * Tells {@code zombie} it killed {@code victim}, as a dying mob tells its killer, and returns whether
     * the victim should drop its loot. 1.21.2 passes the damage source as well.
     */
    static boolean zombieKills(Mob zombie, net.minecraft.world.entity.LivingEntity victim) {
        net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) zombie.level();
        //? if >=1.21.2 {
        return zombie.killedEntity(level, victim, zombie.damageSources().mobAttack(zombie));
        //?} else
        //return zombie.killedEntity(level, victim);
    }
}
