package com.civillagers.platform;

import net.minecraft.world.item.trading.MerchantOffers;

/**
 * What {@code mixin/ZombieVillagerMixin} adds to every zombie villager: the trades it keeps for the cure,
 * which vanilla stores but never hands out. A zombie guard keeps its guard's trades there, as a zombie
 * villager keeps a villager's ({@link com.civillagers.entity.ZombieVillagers}). Cast a zombie villager to
 * this to read them.
 */
public interface ZombieVillagerOffers {
    /** The trades the zombie keeps for the cure, or null if it has none. */
    MerchantOffers civ$tradeOffers();
}
