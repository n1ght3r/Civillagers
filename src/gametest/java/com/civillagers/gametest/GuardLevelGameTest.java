package com.civillagers.gametest;

import com.civillagers.entity.CivEntities;
import com.civillagers.entity.guard.Guard;
import com.civillagers.entity.guard.GuardGear;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;


/**
 * A guard's five levels: vanilla's experience thresholds, the gear each level rolls and how a level-up
 * upgrades it, trading, the experience of a kill, the spawn egg, and saving.
 */
public final class GuardLevelGameTest {
    private static final BlockPos GUARD = new BlockPos(1, 2, 1);
    /** Rolls per check of the tables: enough that a percentage is off by under 4 points almost always. */
    private static final int ROLLS = 2000;
    private static final double TOLERANCE = 0.04;

    @GameTest
    public void aGuardLevelsUpAtVanillasThresholds(GameTestHelper helper) {
        TestFixtures.floor(helper, 1);
        Guard guard = helper.spawnWithNoFreeWill(CivEntities.guard(), GUARD);
        TestFixtures.check(helper, guard.getOffers().size() == 2, "a novice knows two trades");
        guard.addXp(9);
        TestFixtures.check(helper, !guard.canLevelUp(), "9 experience is not enough for an apprentice");
        guard.addXp(1);
        TestFixtures.check(helper, guard.canLevelUp(), "10 is, as for a villager");
        for (int i = 0; i < Guard.LEVEL_UP_DELAY; i++) guard.aiStep();
        TestFixtures.check(helper, guard.villagerLevel() == 2, "the guard should be an apprentice two seconds later, not " + guard.villagerLevel());
        TestFixtures.check(helper, guard.getOffers().size() == 4, "and know two more trades, not " + guard.getOffers().size());

        guard.addXp(1000);
        for (int i = 0; i < 4 * Guard.LEVEL_UP_DELAY; i++) guard.aiStep();
        TestFixtures.check(helper, guard.villagerLevel() == Guard.MAX_LEVEL, "enough experience for several levels takes them one after another");
        TestFixtures.check(helper, guard.getOffers().size() == 2 * Guard.MAX_LEVEL, "a master knows ten trades");
        guard.levelUp();
        TestFixtures.check(helper, guard.villagerLevel() == Guard.MAX_LEVEL, "there is nothing above master");
        helper.succeed();
    }

    @GameTest
    public void aTradeGivesTheGuardItsExperience(GameTestHelper helper) {
        TestFixtures.floor(helper, 1);
        Guard guard = helper.spawnWithNoFreeWill(CivEntities.guard(), GUARD);
        MerchantOffer offer = guard.getOffers().get(0);
        guard.notifyTrade(offer);
        TestFixtures.check(helper, offer.getUses() == 1, "the trade is used once");
        TestFixtures.check(helper, guard.getVillagerXp() == offer.getXp(), "the guard gains the trade's experience, " + offer.getXp());
        helper.succeed();
    }

    @GameTest
    public void eachLevelRollsTheGearOfItsTable(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        RandomSource random = RandomSource.create(7L);
        // Level 1: a helmet 40% of the time, always boots, no chestplate or shield, wood 70% and stone 30%.
        checkShare(helper, "a novice's helmet", 0.40, ROLLS, i -> !GuardGear.roll(EquipmentSlot.HEAD, 1, random, registries).isEmpty());
        checkShare(helper, "a novice's leather boots", 1.0, ROLLS, i -> GuardGear.roll(EquipmentSlot.FEET, 1, random, registries).is(Items.LEATHER_BOOTS));
        checkShare(helper, "a novice's chestplate", 0.0, ROLLS, i -> !GuardGear.roll(EquipmentSlot.CHEST, 1, random, registries).isEmpty());
        checkShare(helper, "a novice's wooden sword", 0.70, ROLLS, i -> GuardGear.roll(EquipmentSlot.MAINHAND, 1, random, registries).is(Items.WOODEN_SWORD));
        // Level 3: chain 35% and iron 65%, a shield half of the time, an iron sword always, nothing enchanted.
        checkShare(helper, "a journeyman's iron boots", 0.65, ROLLS, i -> GuardGear.roll(EquipmentSlot.FEET, 3, random, registries).is(Items.IRON_BOOTS));
        checkShare(helper, "a journeyman's shield", 0.50, ROLLS, i -> !GuardGear.roll(EquipmentSlot.OFFHAND, 3, random, registries).isEmpty());
        checkShare(helper, "a journeyman's iron sword", 1.0, ROLLS, i -> GuardGear.roll(EquipmentSlot.MAINHAND, 3, random, registries).is(Items.IRON_SWORD));
        checkShare(helper, "a journeyman's enchanted boots", 0.0, ROLLS, i -> GuardGear.roll(EquipmentSlot.FEET, 3, random, registries).isEnchanted());
        // Level 5: diamond armor 25%, enchanted 50%, a shield 95%, a diamond sword 40%.
        checkShare(helper, "a master's diamond chestplate", 0.25, ROLLS, i -> GuardGear.roll(EquipmentSlot.CHEST, 5, random, registries).is(Items.DIAMOND_CHESTPLATE));
        checkShare(helper, "a master's enchanted chestplate", 0.50, ROLLS, i -> GuardGear.roll(EquipmentSlot.CHEST, 5, random, registries).isEnchanted());
        checkShare(helper, "a master's shield", 0.95, ROLLS, i -> !GuardGear.roll(EquipmentSlot.OFFHAND, 5, random, registries).isEmpty());
        checkShare(helper, "a master's diamond sword", 0.40, ROLLS, i -> GuardGear.roll(EquipmentSlot.MAINHAND, 5, random, registries).is(Items.DIAMOND_SWORD));
        helper.succeed();
    }

    @GameTest
    public void levellingUpNeverTakesGearAwayAndKeepsTheTablesOdds(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        RandomSource random = RandomSource.create(11L);
        Item[] chestplates = { Items.LEATHER_CHESTPLATE, Items.CHAINMAIL_CHESTPLATE, Items.IRON_CHESTPLATE, Items.DIAMOND_CHESTPLATE };
        int[] helmets = new int[Guard.MAX_LEVEL + 1];
        int[] chests = new int[Guard.MAX_LEVEL + 1];
        int[] ironChests = new int[Guard.MAX_LEVEL + 1];
        int[] enchantedChests = new int[Guard.MAX_LEVEL + 1];
        int[] diamondSwords = new int[Guard.MAX_LEVEL + 1];
        for (int i = 0; i < ROLLS; i++) {
            ItemStack helmet = GuardGear.roll(EquipmentSlot.HEAD, 1, random, registries);
            ItemStack chest = GuardGear.roll(EquipmentSlot.CHEST, 1, random, registries);
            ItemStack sword = GuardGear.roll(EquipmentSlot.MAINHAND, 1, random, registries);
            for (int level = 1; level <= Guard.MAX_LEVEL; level++) {
                if (level > 1) {
                    ItemStack chestBefore = chest;
                    helmet = GuardGear.upgrade(EquipmentSlot.HEAD, helmet, level - 1, random, registries);
                    chest = GuardGear.upgrade(EquipmentSlot.CHEST, chest, level - 1, random, registries);
                    sword = GuardGear.upgrade(EquipmentSlot.MAINHAND, sword, level - 1, random, registries);
                    if (!chestBefore.isEmpty()) {
                        TestFixtures.check(helper, !chest.isEmpty(), "a chestplate is never lost at level " + level);
                        TestFixtures.check(helper, indexOf(chestplates, chest.getItem()) >= indexOf(chestplates, chestBefore.getItem()),
                                "a chestplate is never swapped for a worse one at level " + level);
                        TestFixtures.check(helper, !chestBefore.isEnchanted() || chest.isEnchanted(), "nor loses its enchantments");
                    }
                }
                if (!helmet.isEmpty()) helmets[level]++;
                if (!chest.isEmpty()) chests[level]++;
                if (chest.is(Items.IRON_CHESTPLATE)) ironChests[level]++;
                if (chest.isEnchanted()) enchantedChests[level]++;
                if (sword.is(Items.DIAMOND_SWORD)) diamondSwords[level]++;
            }
        }
        checkShare(helper, "helmets at level 2 after levelling up", 0.75, helmets[2]);
        checkShare(helper, "chestplates at level 2 after levelling up", 0.45, chests[2]);
        checkShare(helper, "chestplates at level 3 after levelling up", 0.85, chests[3]);
        checkShare(helper, "iron chestplates at level 3 after levelling up", 0.85 * 0.65, ironChests[3]);
        checkShare(helper, "enchanted chestplates at level 4 after levelling up", 0.20, enchantedChests[4]);
        checkShare(helper, "enchanted chestplates at level 5 after levelling up", 0.50, enchantedChests[5]);
        checkShare(helper, "diamond swords at level 4 after levelling up", 0.10, diamondSwords[4]);
        checkShare(helper, "diamond swords at level 5 after levelling up", 0.40, diamondSwords[5]);
        helper.succeed();
    }

    @GameTest
    public void aLevelUpUpgradesTheGuardsOwnGearUnderAPlayers(GameTestHelper helper) {
        TestFixtures.floor(helper, 1);
        Guard guard = helper.spawnWithNoFreeWill(CivEntities.guard(), GUARD);
        guard.setOwnGear(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_SWORD));
        guard.equipGear(new ItemStack(Items.BOW));
        guard.levelUp();
        guard.levelUp();
        TestFixtures.check(helper, guard.getMainHandItem().is(Items.BOW), "the bow a player gave stays in the guard's hand");
        TestFixtures.check(helper, guard.ownGear(EquipmentSlot.MAINHAND).is(Items.IRON_SWORD),
                "while its own sword under it is a journeyman's iron one, not " + guard.ownGear(EquipmentSlot.MAINHAND));
        TestFixtures.check(helper, guard.takeGear().is(Items.BOW) && guard.getMainHandItem().is(Items.IRON_SWORD),
                "taking the bow back puts the iron sword in its hand");
        TestFixtures.check(helper, !guard.getItemBySlot(EquipmentSlot.FEET).isEmpty() && !guard.isPlayerGear(EquipmentSlot.FEET),
                "a level-up gives it boots of its own");
        TestFixtures.check(helper, guard.takeGear().isEmpty(), "and its own gear never comes off");
        helper.succeed();
    }

    @GameTest
    public void aSpawnEggMakesAGuardOfAnyLevel(GameTestHelper helper) {
        TestFixtures.floor(helper, 1);
        boolean[] seen = new boolean[Guard.MAX_LEVEL + 1];
        for (int i = 0; i < 30; i++) {
            Guard guard = helper.spawnWithNoFreeWill(CivEntities.guard(), GUARD);
            guard.finalizeSpawn(helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(guard.blockPosition()),
                    EntitySpawnReason.DISPENSER, null);
            int level = guard.villagerLevel();
            seen[level] = true;
            TestFixtures.check(helper, guard.getVillagerXp() == Guard.XP_TO_REACH[level], "a level " + level + " guard has that level's experience");
            TestFixtures.check(helper, guard.getOffers().size() == 2 * level, "and two trades for each level, not " + guard.getOffers().size());
            TestFixtures.check(helper, !guard.getItemBySlot(EquipmentSlot.FEET).isEmpty(), "every guard has boots");
            guard.discard();
        }
        int levels = 0;
        for (boolean found : seen) if (found) levels++;
        TestFixtures.check(helper, levels >= 3, "30 eggs should make guards of several levels, not " + levels);
        helper.succeed();
    }

    @GameTest
    public void aMobAGuardKillsGivesItsExperienceToTheGuard(GameTestHelper helper) {
        TestFixtures.floor(helper, 1);
        Guard guard = helper.spawnWithNoFreeWill(CivEntities.guard(), GUARD);
        Mob zombie = helper.spawnWithNoFreeWill(TestFixtures.zombie(), new BlockPos(3, 2, 1));
        zombie.die(guard.damageSources().mobAttack(guard));
        // A zombie with nothing on is worth vanilla's 5.
        TestFixtures.check(helper, guard.getVillagerXp() == 5, "the guard gains the zombie's 5 experience, not " + guard.getVillagerXp());
        TestFixtures.check(helper, helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, guard.getBoundingBox().inflate(8.0)).isEmpty(),
                "and no orbs drop for it");
        helper.succeed();
    }

    @GameTest
    public void aGuardThatKillsAZombieEndsUpWithItsExperience(GameTestHelper helper) {
        TestFixtures.floor(helper, 1);
        Guard guard = helper.spawn(CivEntities.guard(), GUARD);
        guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
        // Beside the guard and already its target: a test has a second, not the time to look and walk.
        Mob zombie = helper.spawn(TestFixtures.zombie(), new BlockPos(2, 2, 1));
        // A helmet keeps it from burning, and one point of health from a long fight.
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        zombie.setHealth(1.0F);
        guard.setTarget(zombie);
        helper.succeedWhen(() -> {
            TestFixtures.check(helper, !zombie.isAlive(), "the guard should kill the zombie");
            TestFixtures.check(helper, guard.getTarget() == null, "and let go of it once it is dead");
            TestFixtures.check(helper, guard.getVillagerXp() > 0, "and gain its experience, not have " + guard.getVillagerXp());
        });
    }

    @GameTest
    public void aGuardLetsGoOfADeadTarget(GameTestHelper helper) {
        TestFixtures.floor(helper, 1);
        Guard guard = helper.spawnWithNoFreeWill(CivEntities.guard(), GUARD);
        guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        Mob zombie = helper.spawnWithNoFreeWill(TestFixtures.zombie(), new BlockPos(3, 2, 1));
        // Set straight, as the raid goal does, with no target goal to drop it.
        guard.setTarget(zombie);
        zombie.discard();
        guard.aiStep();
        TestFixtures.check(helper, guard.getTarget() == null, "a guard should let go of a target that is gone");
        helper.succeed();
    }

    @GameTest
    public void onlyAMasterAlwaysOutrunsAVindicator(GameTestHelper helper) {
        TestFixtures.floor(helper, 1);
        Guard guard = helper.spawnWithNoFreeWill(CivEntities.guard(), GUARD);
        double vindicator = 0.35;
        double previous = 0.0;
        for (int level = 1; level <= Guard.MAX_LEVEL; level++) {
            guard.setVillagerLevel(level);
            guard.setSpeedVariant(-Guard.SPEED_VARIANT_RANGE);
            double slowest = guard.getAttributeValue(Attributes.MOVEMENT_SPEED) * guard.runSpeed();
            guard.setSpeedVariant(Guard.SPEED_VARIANT_RANGE);
            double fastest = guard.getAttributeValue(Attributes.MOVEMENT_SPEED) * guard.runSpeed();
            TestFixtures.check(helper, slowest > previous, "level " + level + " runs faster than the level below");
            previous = slowest;
            if (level < Guard.MAX_LEVEL) {
                guard.setSpeedVariant(0);
                double middle = guard.getAttributeValue(Attributes.MOVEMENT_SPEED) * guard.runSpeed();
                TestFixtures.check(helper, middle < vindicator, "a level " + level + " guard of middling pace runs slower than a vindicator, not " + middle);
            } else {
                TestFixtures.check(helper, slowest > vindicator, "a master always outruns a vindicator, not " + slowest);
            }
            if (level == 1) {
                TestFixtures.check(helper, slowest > 0.25 && fastest < 0.30, "a novice runs a little faster than a villager walks (0.25)");
            }
        }
        helper.succeed();
    }

    @GameTest
    public void eachGuardHasAPaceOfItsOwn(GameTestHelper helper) {
        TestFixtures.floor(helper, 1);
        java.util.Set<Integer> paces = new java.util.HashSet<>();
        for (int i = 0; i < 10; i++) {
            Guard guard = helper.spawnWithNoFreeWill(CivEntities.guard(), GUARD);
            int pace = guard.speedVariant();
            paces.add(pace);
            TestFixtures.check(helper, Math.abs(pace) <= Guard.SPEED_VARIANT_RANGE, "a pace stays within five percent");
            TestFixtures.check(helper, Math.abs(guard.getAttributeValue(Attributes.MOVEMENT_SPEED) - Guard.BASE_SPEED * (1 + pace / 1000.0)) < 1.0E-6,
                    "and moves the guard's speed by as much");
            guard.discard();
        }
        TestFixtures.check(helper, paces.size() > 3, "guards do not all walk alike: " + paces);

        Guard guard = helper.spawnWithNoFreeWill(CivEntities.guard(), GUARD);
        guard.setSpeedVariant(37);
        TestFixtures.MemorySave saved = new TestFixtures.MemorySave();
        guard.saveExtra(saved);
        Guard reloaded = helper.spawnWithNoFreeWill(CivEntities.guard(), GUARD);
        reloaded.loadExtra(saved);
        TestFixtures.check(helper, reloaded.speedVariant() == 37, "a guard keeps its pace through a reload");
        helper.succeed();
    }

    @GameTest
    public void armorCountsOnAGuardAsOnAPlayer(GameTestHelper helper) {
        TestFixtures.floor(helper, 1);
        Guard guard = helper.spawnWithNoFreeWill(CivEntities.guard(), GUARD);
        guard.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
        guard.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
        // Vanilla applies worn gear's modifiers on the wearer's next tick.
        guard.tick();
        TestFixtures.check(helper, guard.getArmorValue() == 8 + 5, "a netherite chestplate and iron leggings give 13 armor, not " + guard.getArmorValue());
        TestFixtures.check(helper, Math.abs(guard.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) - 0.1) < 1.0E-6,
                "and netherite, the one armor that has it, its knockback resistance, not " + guard.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
        helper.succeed();
    }

    @GameTest
    public void aGuardSavesItsLevelTradesAndGearUnderAPlayers(GameTestHelper helper) {
        TestFixtures.floor(helper, 1);
        Guard guard = helper.spawnWithNoFreeWill(CivEntities.guard(), GUARD);
        guard.setVillagerLevel(4);
        guard.addXp(160);
        int trades = guard.getOffers().size();
        guard.setOwnGear(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        guard.equipGear(new ItemStack(Items.DIAMOND_HELMET));
        TestFixtures.MemorySave saved = new TestFixtures.MemorySave();
        guard.saveExtra(saved);

        Guard reloaded = helper.spawnWithNoFreeWill(CivEntities.guard(), GUARD);
        reloaded.loadExtra(saved);
        TestFixtures.check(helper, reloaded.villagerLevel() == 4 && reloaded.getVillagerXp() == 160, "level and experience survive a reload");
        TestFixtures.check(helper, reloaded.getOffers().size() == trades, "so do its trades");
        // Equipment and drop chances are vanilla's to save; here only what the guard keeps under a player's piece.
        reloaded.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        reloaded.setDropChance(EquipmentSlot.HEAD, 2.0F);
        TestFixtures.check(helper, reloaded.ownGear(EquipmentSlot.HEAD).is(Items.IRON_HELMET), "and its own helmet under the player's");
        helper.succeed();
    }

    /** Checks that {@code hits} out of {@link #ROLLS} is within {@link #TOLERANCE} of {@code expected}. */
    private static void checkShare(GameTestHelper helper, String what, double expected, int hits) {
        double share = hits / (double) ROLLS;
        TestFixtures.check(helper, Math.abs(share - expected) <= TOLERANCE,
                what + ": " + Math.round(share * 100) + "%, expected " + Math.round(expected * 100) + "%");
    }

    private static void checkShare(GameTestHelper helper, String what, double expected, int rolls, java.util.function.IntPredicate roll) {
        int hits = 0;
        for (int i = 0; i < rolls; i++) if (roll.test(i)) hits++;
        checkShare(helper, what, expected, hits);
    }

    private static int indexOf(Item[] items, Item item) {
        for (int i = 0; i < items.length; i++) if (items[i] == item) return i;
        return -1;
    }
}
