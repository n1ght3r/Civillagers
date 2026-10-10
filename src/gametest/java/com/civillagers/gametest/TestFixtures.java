package com.civillagers.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** What several tests need, kept the same on every Minecraft version. */
final class TestFixtures {
    private TestFixtures() { }

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

    /** The creeper's entity type; see {@link #zombie()}. */
    static EntityType<? extends Mob> creeper() {
        //? if >=26.2 {
        return net.minecraft.world.entity.EntityTypes.CREEPER;
        //?} else
        //return EntityType.CREEPER;
    }

    /** Lays a stone floor across the test's 8x8 patch at {@code y}, so mobs stand at {@code y + 1}. */
    static void floor(GameTestHelper helper, int y) {
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
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

    /** A player using {@code hand} on {@code mob}. By 26.1 an interaction also carries where the mob was hit. */
    static void interact(Mob mob, net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
        //? if >=26.1 {
        mob.interact(player, hand, mob.position());
        //?} else
        //mob.interact(player, hand);
    }
}
