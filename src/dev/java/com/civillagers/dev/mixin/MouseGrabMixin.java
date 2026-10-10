package com.civillagers.dev.mixin;

import com.civillagers.dev.agent.client.ClientWindow;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Leaves the mouse pointer alone on a client an agent drives, so the desktop around the window stays
 * usable. See {@link ClientWindow} for why, and why it is off unless {@code -Pdriven} or {@code -Pagent}.
 */
@Mixin(MouseHandler.class)
abstract class MouseGrabMixin {
    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
    private void civ$keepPointerFree(CallbackInfo info) {
        if (ClientWindow.driven()) info.cancel();
    }
}
