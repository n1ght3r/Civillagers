package com.civillagers.gametest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;

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
}
