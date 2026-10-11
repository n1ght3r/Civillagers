package com.civillagers.entity.guard;

import com.civillagers.platform.Vanilla;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * The gear a guard wears for its level: its own, which it never drops and nobody takes off it. A level
 * changes no stat; it changes the gear, by these tables, in percent, one column per level 1 to 5:
 *
 * <pre>
 *            has it                 armor material (leather, chain, iron, diamond)   sword (wood, stone, iron, diamond)
 *   helmet   40  75 100 100 100     1: 100  0   0   0                                1: 70 30   0   0
 *   chest     0  45  85 100 100     2:  40 60   0   0                                2:  0 80  20   0
 *   legs      0  30  75 100 100     3:   0 35  65   0                                3:  0  0 100   0
 *   boots   100 100 100 100 100     4:   0  0 100   0                                4:  0  0  90  10
 *   shield    0  20  50  75  95     5:   0  0  75  25                                5:  0  0  60  40
 * </pre>
 *
 * <p>Each armor piece is enchanted 0, 0, 0, 20 and 50 percent of the time. A new guard {@linkplain #roll
 * rolls} its gear for its level straight from the tables. A guard that levels up {@linkplain #upgrade
 * upgrades} what it wears instead, one level at a time: a piece it has is never lost, swapped for a worse
 * one or stripped of its enchantments, and the chances above still hold at every level. That works
 * because each column is at least as good as the one before it, so a piece only ever moves up: a slot
 * that is empty fills with the chance it gains between the two levels, and a material keeps its place in
 * the order of the old column, drawn again inside that place, and reads off the new column there.
 *
 * <p>One new guard in seven holds a bow instead of a sword and the shield table, and as many a crossbow
 * ({@link #rollRanged}).
 *
 * <p>Every piece is unbreakable for now: a guard's gear does not wear out.
 */
public final class GuardGear {
    /** The slots a guard's own gear goes in. */
    public static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
    };

    // Rows by level 1 to 5, in percent.
    private static final int[] HELMET = { 40, 75, 100, 100, 100 };
    private static final int[] CHESTPLATE = { 0, 45, 85, 100, 100 };
    private static final int[] LEGGINGS = { 0, 30, 75, 100, 100 };
    private static final int[] BOOTS = { 100, 100, 100, 100, 100 };
    private static final int[] SHIELD = { 0, 20, 50, 75, 95 };
    private static final int[] WEAPON = { 100, 100, 100, 100, 100 };
    private static final int[] ENCHANTED_ARMOR = { 0, 0, 0, 20, 50 };
    private static final int[] NEVER = { 0, 0, 0, 0, 0 };
    private static final int[][] ARMOR_MATERIAL = {
            { 100, 0, 0, 0 }, { 40, 60, 0, 0 }, { 0, 35, 65, 0 }, { 0, 0, 100, 0 }, { 0, 0, 75, 25 },
    };
    private static final int[][] WEAPON_MATERIAL = {
            { 70, 30, 0, 0 }, { 0, 80, 20, 0 }, { 0, 0, 100, 0 }, { 0, 0, 90, 10 }, { 0, 0, 60, 40 },
    };
    private static final int[][] ONE_MATERIAL = { { 100 }, { 100 }, { 100 }, { 100 }, { 100 } };

    /** In percent, at every level: how often a new guard is an archer, with a bow, and with a crossbow. */
    public static final int BOW_CHANCE = 15;
    public static final int CROSSBOW_CHANCE = 15;

    private GuardGear() { }

    /**
     * A new guard's own bow ({@link #BOW_CHANCE}) or crossbow ({@link #CROSSBOW_CHANCE}), unbreakable, or
     * an empty stack for a guard who fights with a sword from {@link #roll}. An archer has no shield: both
     * its hands are for the bow, and a level-up never gives it one. Its bow stays as it is through the
     * levels, as a piece that is not in the tables does.
     */
    public static ItemStack rollRanged(RandomSource random) {
        int roll = random.nextInt(100);
        if (roll < BOW_CHANCE) return own(Items.BOW);
        if (roll < BOW_CHANCE + CROSSBOW_CHANCE) return own(Items.CROSSBOW);
        return ItemStack.EMPTY;
    }

    /** Whether {@code stack} is a bow or a crossbow. */
    public static boolean isRanged(ItemStack stack) {
        return stack.is(Items.BOW) || stack.is(Items.CROSSBOW);
    }

    /** The guard's own gear in {@code slot} for a new guard of {@code level}, rolled from the tables; empty if it has none there. */
    public static ItemStack roll(EquipmentSlot slot, int level, RandomSource random, RegistryAccess registries) {
        if (random.nextInt(100) >= presence(slot)[index(level)]) return ItemStack.EMPTY;
        return make(slot, level, random, registries);
    }

    /** A piece for {@code slot} of the material and enchantment {@code level} gives, once it is decided the guard has one. */
    private static ItemStack make(EquipmentSlot slot, int level, RandomSource random, RegistryAccess registries) {
        int l = index(level);
        ItemStack piece = own(items(slot)[pick(materials(slot)[l], random.nextInt(100))]);
        if (random.nextInt(100) < enchanted(slot)[l]) piece = enchant(piece, level, random, registries);
        return piece;
    }

    /**
     * What a guard wearing {@code worn} as its own gear in {@code slot} wears once it goes from
     * {@code from} up to {@code from + 1}: the same piece, a better one, or a new one where it had none.
     * A piece that is not one of the tables' (given by a command) is left as it is.
     */
    public static ItemStack upgrade(EquipmentSlot slot, ItemStack worn, int from, RandomSource random, RegistryAccess registries) {
        int was = index(from);
        int now = index(from + 1);
        if (now == was) return worn;
        if (worn.isEmpty()) {
            int had = presence(slot)[was];
            int has = presence(slot)[now];
            // The chance of a piece it gains between the two levels, so that it has one with the new level's odds.
            int chance = had >= 100 ? has : (has - had) * 100 / (100 - had);
            return random.nextInt(100) < chance ? make(slot, from + 1, random, registries) : ItemStack.EMPTY;
        }
        Item[] items = items(slot);
        int material = indexOf(items, worn.getItem());
        if (material < 0) return worn;
        int[] before = materials(slot)[was];
        int[] after = materials(slot)[now];
        int better;
        if (before[material] > 0) {
            // Where this material sits in the old column, drawn again inside it, and read off the new one.
            better = pick(after, cumulative(before, material) + random.nextInt(before[material]));
        } else {
            // A material the old level never gives: draw the new level afresh, keeping the better.
            better = pick(after, random.nextInt(100));
        }
        boolean enchant = !worn.isEnchanted() && random.nextInt(100) < gained(enchanted(slot), was, now);
        if (better <= material) return enchant ? enchant(worn.copy(), from + 1, random, registries) : worn;
        ItemStack piece = own(items[better]);
        return enchant || worn.isEnchanted() ? enchant(piece, from + 1, random, registries) : piece;
    }

    /** The chance in percent that something {@code row} gives at {@code now} but not at {@code was} turns up between them. */
    private static int gained(int[] row, int was, int now) {
        int had = row[was];
        return had >= 100 ? 0 : (row[now] - had) * 100 / (100 - had);
    }

    /** One {@code item}, unbreakable. */
    private static ItemStack own(Item item) {
        ItemStack stack = new ItemStack(item);
        Vanilla.makeUnbreakable(stack);
        return stack;
    }

    /**
     * Enchants {@code piece} as vanilla enchants a mob's spawn gear, with enchantments that may go on it:
     * as from an enchanting table at 5 to 15 levels for a level 4 guard, 15 to 30 for a master.
     */
    private static ItemStack enchant(ItemStack piece, int level, RandomSource random, RegistryAccess registries) {
        int power = level >= 5 ? 15 + random.nextInt(16) : 5 + random.nextInt(11);
        return EnchantmentHelper.enchantItem(random, piece, power, registries,
                registries.lookupOrThrow(Registries.ENCHANTMENT).get(EnchantmentTags.ON_MOB_SPAWN_EQUIPMENT));
    }

    /** The index of the first entry of {@code row} whose running total passes {@code roll}, 0 to 99. */
    private static int pick(int[] row, int roll) {
        int total = 0;
        for (int i = 0; i < row.length; i++) {
            total += row[i];
            if (roll < total) return i;
        }
        return row.length - 1;
    }

    private static int cumulative(int[] row, int before) {
        int total = 0;
        for (int i = 0; i < before; i++) total += row[i];
        return total;
    }

    private static int indexOf(Item[] items, Item item) {
        for (int i = 0; i < items.length; i++) if (items[i] == item) return i;
        return -1;
    }

    private static int index(int level) {
        return Math.max(1, Math.min(level, Guard.MAX_LEVEL)) - 1;
    }

    private static int[] presence(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> HELMET;
            case CHEST -> CHESTPLATE;
            case LEGS -> LEGGINGS;
            case FEET -> BOOTS;
            case OFFHAND -> SHIELD;
            case MAINHAND -> WEAPON;
            default -> NEVER;
        };
    }

    private static int[][] materials(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD, CHEST, LEGS, FEET -> ARMOR_MATERIAL;
            case MAINHAND -> WEAPON_MATERIAL;
            default -> ONE_MATERIAL;
        };
    }

    private static int[] enchanted(EquipmentSlot slot) {
        return slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR ? ENCHANTED_ARMOR : NEVER;
    }

    /** The pieces for {@code slot}, worst first, in the order of the material columns. */
    private static Item[] items(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> new Item[] { Items.LEATHER_HELMET, Items.CHAINMAIL_HELMET, Items.IRON_HELMET, Items.DIAMOND_HELMET };
            case CHEST -> new Item[] { Items.LEATHER_CHESTPLATE, Items.CHAINMAIL_CHESTPLATE, Items.IRON_CHESTPLATE, Items.DIAMOND_CHESTPLATE };
            case LEGS -> new Item[] { Items.LEATHER_LEGGINGS, Items.CHAINMAIL_LEGGINGS, Items.IRON_LEGGINGS, Items.DIAMOND_LEGGINGS };
            case FEET -> new Item[] { Items.LEATHER_BOOTS, Items.CHAINMAIL_BOOTS, Items.IRON_BOOTS, Items.DIAMOND_BOOTS };
            case MAINHAND -> new Item[] { Items.WOODEN_SWORD, Items.STONE_SWORD, Items.IRON_SWORD, Items.DIAMOND_SWORD };
            default -> new Item[] { Items.SHIELD };
        };
    }
}
