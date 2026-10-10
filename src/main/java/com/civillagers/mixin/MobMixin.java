package com.civillagers.mixin;

import com.civillagers.ai.GuardEnemies;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets zombies and raiders go after guards ({@link GuardEnemies}). A mob registers its goals in
 * {@link Mob}'s constructor, on the server only, so the guard target goes in right after, once, for every
 * kind of mob at one place.
 */
@Mixin(Mob.class)
abstract class MobMixin {
    @Shadow
    @Final
    protected GoalSelector targetSelector;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void civ$addGuardTarget(EntityType<? extends Mob> type, Level level, CallbackInfo info) {
        if (level != null && !level.isClientSide()) GuardEnemies.addTargetGoal((Mob) (Object) this, targetSelector);
    }
}
