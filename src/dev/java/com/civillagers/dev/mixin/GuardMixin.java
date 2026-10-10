package com.civillagers.dev.mixin;

import com.civillagers.dev.benchmark.PlayerPresence;
import com.civillagers.entity.Guard;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets the benchmark decide whether guards have a player near ({@link PlayerPresence}), so it can measure a
 * watched guard and an idle one on a server nobody has joined. The mod's own class, so nothing to remap.
 * Development only: this mod never ships.
 */
@Mixin(value = Guard.class, remap = false)
abstract class GuardMixin {
    @Inject(method = "anyPlayerWithin", at = @At("HEAD"), cancellable = true)
    private static void civ$forcedPresence(Level level, double x, double y, double z, double range,
                                           CallbackInfoReturnable<Boolean> info) {
        Boolean forced = PlayerPresence.forced();
        if (forced != null) info.setReturnValue(forced);
    }
}
