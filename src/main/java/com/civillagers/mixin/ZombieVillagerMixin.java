package com.civillagers.mixin;

import com.civillagers.entity.ZombieGuards;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * When the cure of a zombie villager that was a guard finishes, it comes back a guard instead of a
 * villager ({@link ZombieGuards#cure}). Any other zombie villager is left to vanilla.
 */
@Mixin(ZombieVillager.class)
abstract class ZombieVillagerMixin {
    @Inject(method = "finishConversion", at = @At("HEAD"), cancellable = true)
    private void civ$cureGuard(ServerLevel level, CallbackInfo info) {
        if (ZombieGuards.cure(level, (ZombieVillager) (Object) this) != null) info.cancel();
    }
}
