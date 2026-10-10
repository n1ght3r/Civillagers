package com.civillagers.dev.agent.client;

import net.minecraft.client.Minecraft;

/**
 * What a client that is being driven rather than played does with the mouse pointer and its window.
 *
 * <p>A driven client is one an agent sends requests to while the person who launched it works on the same
 * desktop. Minecraft takes the cursor as soon as its window is focused with a world open and holds it
 * inside the frame until a screen opens, so clicking the window, or the window coming up in front, costs
 * the pointer everywhere else. A driven client needs none of it: every command goes through the player's
 * connection and every view change through the player's rotation, so nothing here is ever steered by a
 * physical mouse. {@code MouseGrabMixin} refuses the grab outright rather than releasing it a tick later,
 * so the cursor is never hidden and never warped to the middle of the window.
 *
 * <p>The window keeps the size the run task gives it and is never maximised or made full screen. It also
 * stops pausing when it loses focus, since an unattended window is never focused.
 *
 * <p>It is off by default: without the grab there is no mouse look and no click reaching the world, so a
 * person cannot play the client. {@code -Pdriven} turns it on, and {@code -Pagent=<file>} implies it.
 */
public final class ClientWindow {
    /** Read once, from system properties, because {@code MouseGrabMixin} asks before the mod initializes. */
    private static final boolean DRIVEN = Boolean.getBoolean("civillagers.agent.driven")
            || System.getProperty("civillagers.agent.script") != null;

    private ClientWindow() { }

    /** Whether this client is being driven by an agent rather than played by a person. */
    public static boolean driven() {
        return DRIVEN;
    }

    /**
     * Turns off the focus pause and gives the pointer back, once, on the first client tick. The release is
     * not redundant with the mixin: the mixin only refuses the next grab.
     */
    public static void open(Minecraft minecraft) {
        if (!DRIVEN) return;
        minecraft.options.pauseOnLostFocus = false;
        if (minecraft.mouseHandler.isMouseGrabbed()) minecraft.mouseHandler.releaseMouse();
    }

    /**
     * Puts what the agent is doing in the window title once a second: the request it is answering, the
     * ticks it still waits, and how far through the script it is. A long {@code wait} otherwise looks like a
     * hung game to anyone glancing at the window. The title rather than the screen, so it never reaches a
     * capture.
     */
    public static void showStatus(Minecraft minecraft, String status, long tick) {
        if (!DRIVEN || status == null || tick % 20 != 0) return;
        minecraft.getWindow().setTitle("Civillagers agent: " + status);
    }
}
