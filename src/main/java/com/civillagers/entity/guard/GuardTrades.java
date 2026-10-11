package com.civillagers.entity.guard;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * What a guard trades, as a villager does: at each level it learns two trades drawn from that level's
 * pool. A guard pays a bounty for what the monsters it fights drop, and sells the gear of its own rank.
 *
 * <p>Every number is vanilla's where vanilla has the same trade (its data in
 * {@code data/minecraft/villager_trade}, 26.3): price, uses, experience and price multiplier, 0.2 for
 * armor, weapons and the bell and 0.05 for everything else, bows and arrows included, as the fletcher's.
 * An enchanted item costs its base price plus its enchantment level, 5 to 19, at most 64. A trade vanilla
 * has not is priced like the nearest one it has, by how hard the goods are to come by:
 *
 * <pre>
 *   Novice      buys  32 rotten flesh (cleric), 32 bones (as rotten flesh: one a kill)    sells an iron sword (2: an axe is 3, a sword has one ingot less)
 *   Apprentice  buys  14 string (fletcher)                                                  sells 16 arrows (1, fletcher), a chainmail chestplate (4) and leggings (3, armorer), a bell (36, smiths)
 *   Journeyman  buys  4 iron ingots (smiths), 4 gunpowder (as 2 rabbit feet: rarer, riskier) sells a shield (5, armorer)
 *   Expert      buys  1 diamond (smiths), 2 ender pearls (as 2 rabbit feet)                 sells an enchanted iron chestplate (9+, its plain price) and crossbow (3+, fletcher)
 *   Master                                                                                  sells an enchanted diamond sword (8+, weaponsmith), chestplate (16+) and helmet (8+, armorer)
 * </pre>
 *
 * Experience follows vanilla's for the level a guard learns the trade at: 1 or 2 for a novice's, 5 to 10
 * for an apprentice's, 10 to 20 for a journeyman's, 15 to 30 for an expert's, 30 for a master's.
 */
public final class GuardTrades {
    /** How many trades a guard learns at each level. */
    public static final int PER_LEVEL = 2;

    /** Vanilla's price multiplier for what a villager buys, and for goods it sells that are not gear. */
    private static final float GOODS = 0.05F;
    /** Vanilla's price multiplier for armor, weapons and the bell. */
    private static final float GEAR = 0.2F;

    /** One trade a guard may offer. */
    private interface Listing {
        MerchantOffer make(RandomSource random, RegistryAccess registries);
    }

    private static final Listing[][] POOLS = {
            // Novice: bounties on what zombies and skeletons drop, and a plain sword.
            {
                    buy(Items.ROTTEN_FLESH, 32, 16, 2),
                    buy(Items.BONE, 32, 16, 2),
                    sell(Items.IRON_SWORD, 1, 2, 12, 1, GEAR),
            },
            // Apprentice: spiders, an archer's and a chain-clad guard's kit, and the village's alarm bell.
            {
                    buy(Items.STRING, 14, 16, 10),
                    sell(Items.ARROW, 16, 1, 12, 5, GOODS),
                    sell(Items.CHAINMAIL_CHESTPLATE, 1, 4, 12, 5, GEAR),
                    sell(Items.CHAINMAIL_LEGGINGS, 1, 3, 12, 5, GEAR),
                    sell(Items.BELL, 1, 36, 12, 5, GEAR),
            },
            // Journeyman: iron for the village's guards, creepers, and a shield.
            {
                    buy(Items.IRON_INGOT, 4, 12, 20),
                    buy(Items.GUNPOWDER, 4, 12, 20),
                    sell(Items.SHIELD, 1, 5, 12, 10, GEAR),
            },
            // Expert: endermen and diamonds, and enchanted iron.
            {
                    buy(Items.DIAMOND, 1, 12, 30),
                    buy(Items.ENDER_PEARL, 2, 12, 30),
                    enchanted(Items.IRON_CHESTPLATE, 9, 15, GEAR),
                    enchanted(Items.CROSSBOW, 3, 15, GOODS),
            },
            // Master: enchanted diamond.
            {
                    enchanted(Items.DIAMOND_SWORD, 8, 30, GEAR),
                    enchanted(Items.DIAMOND_CHESTPLATE, 16, 30, GEAR),
                    enchanted(Items.DIAMOND_HELMET, 8, 30, GEAR),
            },
    };

    private GuardTrades() { }

    /** Adds {@link #PER_LEVEL} trades of {@code level}'s pool to {@code offers}, two different ones, as a villager learns at a new level. */
    public static void learn(int level, MerchantOffers offers, RandomSource random, RegistryAccess registries) {
        if (level < 1 || level > POOLS.length) return;
        Listing[] pool = POOLS[level - 1];
        int first = random.nextInt(pool.length);
        int second = random.nextInt(pool.length - 1);
        if (second >= first) second++;
        offers.add(pool[first].make(random, registries));
        offers.add(pool[second].make(random, registries));
    }

    /** The guard buys {@code count} of {@code item} for an emerald. */
    private static Listing buy(Item item, int count, int maxUses, int xp) {
        return (random, registries) -> new MerchantOffer(new ItemCost(item, count), new ItemStack(Items.EMERALD), maxUses, xp, GOODS);
    }

    /** The guard sells {@code count} of {@code item} for {@code emeralds}. */
    private static Listing sell(Item item, int count, int emeralds, int maxUses, int xp, float multiplier) {
        return (random, registries) -> new MerchantOffer(new ItemCost(Items.EMERALD, emeralds), new ItemStack(item, count), maxUses, xp, multiplier);
    }

    /** The guard sells {@code item}, enchanted at 5 to 19 levels, for {@code base} emeralds plus that level, as vanilla's smiths do. */
    private static Listing enchanted(Item item, int base, int xp, float multiplier) {
        return (random, registries) -> {
            int power = 5 + random.nextInt(15);
            ItemStack stack = EnchantmentHelper.enchantItem(random, new ItemStack(item), power, registries,
                    registries.lookupOrThrow(Registries.ENCHANTMENT).get(EnchantmentTags.ON_TRADED_EQUIPMENT));
            return new MerchantOffer(new ItemCost(Items.EMERALD, Math.min(base + power, 64)), stack, 3, xp, multiplier);
        };
    }
}
