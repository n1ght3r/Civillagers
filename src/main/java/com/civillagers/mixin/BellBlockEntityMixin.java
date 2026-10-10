package com.civillagers.mixin;

import com.civillagers.entity.CivVillager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets the mod's villagers hear a bell ring, as vanilla lets villagers: event 1 is a ring, and on the
 * server vanilla tells the villagers around through their brains, which the mod's villagers do not use
 * ({@link CivVillager#bellRang}).
 */
@Mixin(BellBlockEntity.class)
abstract class BellBlockEntityMixin {
    @Inject(method = "triggerEvent", at = @At("HEAD"))
    private void civ$alarmVillagers(int id, int data, CallbackInfoReturnable<Boolean> info) {
        if (id != 1) return;
        BlockEntity bell = (BlockEntity) (Object) this;
        if (bell.getLevel() instanceof ServerLevel level) CivVillager.bellRang(level, bell.getBlockPos());
    }
}
