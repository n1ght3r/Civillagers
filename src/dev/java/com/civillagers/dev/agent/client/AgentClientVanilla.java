package com.civillagers.dev.agent.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.util.function.Consumer;

/**
 * The client calls the agent makes whose shape moved between Minecraft versions, and that the mod itself
 * has no reason to have a seam for: the mod's {@code ClientVanilla} carries what players need, and a
 * development tool's screenshot is not that.
 */
final class AgentClientVanilla {
    private AgentClientVanilla() { }

    /** The screen currently open, or null for none. 26.2 moved the field onto the GUI. */
    static Screen screen(Minecraft minecraft) {
        //? if >=26.2 {
        return minecraft.gui.screen();
        //?} else {
        /*return minecraft.screen;
        *///?}
    }

    /** Toggles the same HUD-hidden state as F1. 26.2 moved the flag into Hud and made the toggle public. */
    static void toggleHud(Minecraft minecraft) {
        //? if >=26.2 {
        minecraft.gui.hud.toggle();
        //?} else {
        /*minecraft.options.hideGui = !minecraft.options.hideGui;
        *///?}
    }

    /** Whether the HUD is hidden, as F1 hides it. */
    static boolean hudHidden(Minecraft minecraft) {
        //? if >=26.2 {
        return minecraft.gui.hud.isHidden();
        //?} else {
        /*return minecraft.options.hideGui;
        *///?}
    }

    /**
     * Writes the framebuffer to {@code <directory>/screenshots/<name>} and calls {@code done} with vanilla's
     * own message once the file is on disk, which from 1.21.11 is not in this tick: the pixels are read
     * back from the GPU asynchronously. The window need not be focused or in front.
     */
    static void screenshot(Minecraft minecraft, File directory, String name, Consumer<Component> done) {
        RenderTarget target = mainTarget(minecraft);
        // 1.21.11 added the downscale factor; 1 is the framebuffer's own size.
        //? if >1.21.1 {
        Screenshot.grab(directory, name, target, 1, done);
        //?} else {
        /*Screenshot.grab(directory, name, target, done);
        *///?}
    }

    /** The target the world and the GUI are drawn into. 26.2 moved it onto the game renderer. */
    private static RenderTarget mainTarget(Minecraft minecraft) {
        //? if >=26.2 {
        return minecraft.gameRenderer.mainRenderTarget();
        //?} else {
        /*return minecraft.getMainRenderTarget();
        *///?}
    }
}
