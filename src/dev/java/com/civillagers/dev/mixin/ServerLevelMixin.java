package com.civillagers.dev.mixin;

import com.civillagers.dev.benchmark.EntityTickTimer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Times each entity's tick for {@link EntityTickTimer}, so the benchmark can set a guard's cost beside a
 * vanilla villager's or iron golem's with the same clock. Development only: this mod never ships.
 */
@Mixin(ServerLevel.class)
abstract class ServerLevelMixin {
    @Inject(method = "tickNonPassenger", at = @At("HEAD"))
    private void civ$startEntityTick(Entity entity, CallbackInfo info) {
        EntityTickTimer.begin(entity);
    }

    @Inject(method = "tickNonPassenger", at = @At("RETURN"))
    private void civ$endEntityTick(Entity entity, CallbackInfo info) {
        EntityTickTimer.end(entity);
    }
}
