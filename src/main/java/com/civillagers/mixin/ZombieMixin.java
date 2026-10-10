package com.civillagers.mixin;

import com.civillagers.entity.Guard;
import com.civillagers.entity.ZombieGuards;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets a zombie turn a guard it kills, where vanilla turns a villager ({@link ZombieGuards}). Returning
 * false, as vanilla does for a villager, tells the dying guard to drop nothing: its gear went to the
 * zombie. 1.21.2 passes the damage source as well.
 */
@Mixin(Zombie.class)
abstract class ZombieMixin {
    //? if >=1.21.2 {
    @Inject(method = "killedEntity", at = @At("HEAD"), cancellable = true)
    private void civ$zombifyGuard(ServerLevel level, LivingEntity killed, net.minecraft.world.damagesource.DamageSource source,
                                  CallbackInfoReturnable<Boolean> info) {
        civ$zombify(level, killed, info);
    }
    //?} else {
    /*@Inject(method = "killedEntity", at = @At("HEAD"), cancellable = true)
    private void civ$zombifyGuard(ServerLevel level, LivingEntity killed, CallbackInfoReturnable<Boolean> info) {
        civ$zombify(level, killed, info);
    }
    *///?}

    private void civ$zombify(ServerLevel level, LivingEntity killed, CallbackInfoReturnable<Boolean> info) {
        if (!(killed instanceof Guard guard)) return;
        if (!ZombieGuards.turns(level.getDifficulty(), ((Zombie) (Object) this).getRandom())) return;
        if (ZombieGuards.zombify(level, guard) != null) info.setReturnValue(false);
    }
}
