package com.civillagers.mixin;

import com.civillagers.entity.ZombieVillagers;
import com.civillagers.platform.ZombieVillagerOffers;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * When the cure of a zombie villager that was a villager of the mod, such as a guard, finishes, it comes
 * back as what it was instead of a villager ({@link ZombieVillagers#cure}). Any other zombie villager is
 * left to vanilla. It also hands out the trades the zombie keeps for the cure ({@link ZombieVillagerOffers}),
 * so a cured guard keeps its own.
 */
@Mixin(ZombieVillager.class)
abstract class ZombieVillagerMixin implements ZombieVillagerOffers {
    @Shadow
    private MerchantOffers tradeOffers;

    @Inject(method = "finishConversion", at = @At("HEAD"), cancellable = true)
    private void civ$cureVillager(ServerLevel level, CallbackInfo info) {
        if (ZombieVillagers.cure(level, (ZombieVillager) (Object) this) != null) info.cancel();
    }

    @Override
    public MerchantOffers civ$tradeOffers() {
        return tradeOffers;
    }
}
