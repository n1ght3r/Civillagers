package com.civillagers.gametest;

import com.civillagers.Civillagers;
import com.civillagers.platform.Loader;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** The mod loads on a dedicated server and reports itself loaded. The first test every node runs. */
public final class BootGameTest {
    @GameTest
    public void modIsLoaded(GameTestHelper helper) {
        TestFixtures.check(helper, Loader.isModLoaded(Civillagers.MOD_ID), Civillagers.MOD_ID + " should be loaded");
        helper.succeed();
    }
}
