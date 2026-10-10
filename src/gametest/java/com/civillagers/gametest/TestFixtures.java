package com.civillagers.gametest;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;

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
}
