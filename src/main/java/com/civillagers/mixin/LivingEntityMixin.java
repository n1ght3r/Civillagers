package com.civillagers.mixin;

import com.civillagers.entity.guard.Guard;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A guard that kills a mob gains the experience the mob is worth, as much as it would drop for a player
 * ({@link Guard#killed}), straight away: no orbs drop for it. Hooked where a dying mob drops its
 * experience, which every death goes through; what drops for players is left to vanilla. Nothing for
 * what always drops experience (a player) or has none to give (a baby, or a mob whose experience was
 * already taken).
 *
 * <p>1.21.5 passes the level.
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
    @Shadow
    protected abstract boolean isAlwaysExperienceDropper();

    //? if >=1.21.5 {
    @Inject(method = "dropExperience", at = @At("HEAD"))
    private void civ$rewardGuard(ServerLevel level, Entity killer, CallbackInfo info) {
        civ$rewardGuard(level, killer);
    }
    //?} else {
    /*@Inject(method = "dropExperience", at = @At("HEAD"))
    private void civ$rewardGuard(Entity killer, CallbackInfo info) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level() instanceof ServerLevel level) civ$rewardGuard(level, killer);
    }
    *///?}

    private void civ$rewardGuard(ServerLevel level, Entity killer) {
        if (!(killer instanceof Guard guard) || isAlwaysExperienceDropper()) return;
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.wasExperienceConsumed() || !self.shouldDropExperience()) return;
        guard.killed(self.getExperienceReward(level, guard));
    }
}
