package com.civillagers.gametest;

import com.civillagers.ai.GuardPatrolGoal;
import com.civillagers.ai.GuardSleepGoal;
import com.civillagers.ai.GuardTargetGoal;
import com.civillagers.entity.CivEntities;
import com.civillagers.entity.Guard;
import com.civillagers.village.Village;
import com.civillagers.village.Villages;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Guards: their gear, how often they look for targets, combat, patrol points and beds. */
public final class GuardGameTest {
    private static final BlockPos GUARD = new BlockPos(1, 2, 1);

    @GameTest
    public void aNewGuardCarriesASword(GameTestHelper helper) {
        TestFixtures.floor(helper, 1);
        Guard guard = helper.spawnWithNoFreeWill(CivEntities.guard(), GUARD);
        guard.finalizeSpawn(helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(guard.blockPosition()),
                EntitySpawnReason.MOB_SUMMONED, null);
        TestFixtures.check(helper, guard.getMainHandItem().is(Items.IRON_SWORD), "a new guard should hold an iron sword");
        TestFixtures.check(helper, guard.getMaxHealth() == 30.0F, "a guard has 30 health, not " + guard.getMaxHealth());
        TestFixtures.check(helper, !guard.removeWhenFarAway(1000.0), "guards never despawn");
        helper.succeed();
    }

    @GameTest
    public void aGuardWearsWhatItIsGiven(GameTestHelper helper) {
        TestFixtures.floor(helper, 1);
        Guard guard = helper.spawnWithNoFreeWill(CivEntities.guard(), GUARD);
        TestFixtures.check(helper, guard.equipGear(new ItemStack(Items.DIRT)) == null, "dirt is no gear");
        TestFixtures.check(helper, guard.equipGear(new ItemStack(Items.IRON_HELMET)) != null
                && guard.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET), "a helmet goes on the head");
        TestFixtures.check(helper, guard.gearSlot(new ItemStack(Items.SHIELD)) == EquipmentSlot.OFFHAND, "a shield goes in the off hand");
        TestFixtures.check(helper, guard.gearSlot(new ItemStack(Items.BOW)) == EquipmentSlot.MAINHAND, "a bow is a weapon");
        TestFixtures.check(helper, guard.gearSlot(new ItemStack(Items.IRON_AXE)) == EquipmentSlot.MAINHAND, "an axe is a weapon");
        TestFixtures.check(helper, guard.gearSlot(new ItemStack(Items.IRON_PICKAXE)) == null, "a pickaxe is no weapon");

        ItemStack swordStack = new ItemStack(Items.DIAMOND_SWORD, 2);
        ItemStack previous = guard.equipGear(swordStack);
        TestFixtures.check(helper, previous != null && previous.isEmpty(), "the empty hand had nothing to hand back");
        TestFixtures.check(helper, guard.getMainHandItem().getCount() == 1, "a guard takes one sword, not the stack");

        TestFixtures.check(helper, guard.takeGear().is(Items.DIAMOND_SWORD), "the weapon comes back first");
        TestFixtures.check(helper, guard.takeGear().is(Items.IRON_HELMET), "then the armor, from the top");
        TestFixtures.check(helper, guard.takeGear().isEmpty(), "and then there is nothing left");
        helper.succeed();
    }

    @GameTest
    public void aPlayerHandsAGuardASword(GameTestHelper helper) {
        TestFixtures.floor(helper, 1);
        Guard guard = helper.spawnWithNoFreeWill(CivEntities.guard(), GUARD);
        guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));

        TestFixtures.interact(guard, player, InteractionHand.MAIN_HAND);
        TestFixtures.check(helper, guard.getMainHandItem().is(Items.DIAMOND_SWORD), "the guard should take the diamond sword");
        TestFixtures.check(helper, !player.getInventory().contains(new ItemStack(Items.DIAMOND_SWORD)), "the player gave it away");
        // Into the first free slot, which is the hand the diamond sword just left.
        TestFixtures.check(helper, player.getInventory().contains(new ItemStack(Items.IRON_SWORD)),
                "the player gets the old sword back");
        helper.succeed();
    }

    @GameTest
    public void aGuardLooksForTargetsAtMostEveryTenTicks(GameTestHelper helper) {
        TestFixtures.floor(helper, 1);
        Guard guard = helper.spawnWithNoFreeWill(CivEntities.guard(), GUARD);
        GuardTargetGoal goal = new GuardTargetGoal(guard);
        int start = guard.tickCount;
        int ticks = 200;
        int lastScan = -GuardTargetGoal.SCAN_INTERVAL;
        for (int t = 0; t < ticks; t++) {
            guard.tickCount = start + t;
            int before = goal.scans();
            goal.canUse();
            if (goal.scans() == before) continue;
            TestFixtures.check(helper, t - lastScan >= GuardTargetGoal.SCAN_INTERVAL,
                    "two scans " + (t - lastScan) + " ticks apart, under " + GuardTargetGoal.SCAN_INTERVAL);
            lastScan = t;
        }
        int scans = goal.scans();
        TestFixtures.check(helper, scans <= ticks / GuardTargetGoal.SCAN_INTERVAL + 1, scans + " scans in " + ticks + " ticks is too many");
        TestFixtures.check(helper, scans >= ticks / (2 * GuardTargetGoal.SCAN_INTERVAL), "a guard still looks around: " + scans + " scans");
        helper.succeed();
    }

    @GameTest
    public void aGuardGoesAfterAHostileMob(GameTestHelper helper) {
        TestFixtures.floor(helper, 1);
        Guard guard = helper.spawn(CivEntities.guard(), GUARD);
        guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        Mob zombie = helper.spawn(TestFixtures.zombie(), new BlockPos(3, 2, 1));
        helper.succeedWhen(() -> TestFixtures.check(helper, guard.getTarget() == zombie, "the guard should target the zombie"));
    }

    @GameTest
    public void creepersAreLeftToArchers(GameTestHelper helper) {
        TestFixtures.floor(helper, 1);
        Mob creeper = helper.spawnWithNoFreeWill(TestFixtures.creeper(), new BlockPos(5, 2, 5));
        Mob zombie = helper.spawnWithNoFreeWill(TestFixtures.zombie(), new BlockPos(6, 2, 6));
        TestFixtures.check(helper, !Guard.isThreat(creeper, false), "a guard with a sword leaves creepers alone");
        TestFixtures.check(helper, Guard.isThreat(creeper, true), "a guard with a bow shoots them");
        TestFixtures.check(helper, Guard.isThreat(zombie, false), "zombies are always a threat");
        helper.succeed();
    }

    @GameTest
    public void patrolPointsStayInTheVillage(GameTestHelper helper) {
        TestFixtures.floor(helper, 1);
        BlockPos bell = helper.absolutePos(new BlockPos(4, 2, 4));
        helper.setBlock(new BlockPos(4, 2, 4), Blocks.BELL);
        Villages villages = Villages.get(helper.getLevel());
        Village village = villages.addAnchor(bell, 0L);
        try {
            Guard guard = helper.spawnWithNoFreeWill(CivEntities.guard(), GUARD);
            BlockPos.MutableBlockPos point = new BlockPos.MutableBlockPos();
            int picked = 0;
            for (int i = 0; i < 50; i++) {
                if (!GuardPatrolGoal.pickPoint(helper.getLevel(), guard, guard.getRandom(), point)) continue;
                picked++;
                TestFixtures.check(helper, village.contains(point), point.toShortString() + " is outside the village");
                double dx = point.getX() - guard.getX();
                double dz = point.getZ() - guard.getZ();
                double reach = GuardPatrolGoal.MAX_STEP + 2;
                TestFixtures.check(helper, dx * dx + dz * dz <= reach * reach,
                        point.toShortString() + " is too far for one walk");
            }
            TestFixtures.check(helper, picked > 0, "a guard in a village finds somewhere to patrol");
        } finally {
            villages.removeAnchor(village, bell.asLong(), 0L);
        }
        helper.succeed();
    }

    @GameTest
    public void aGuardFindsAFreeBed(GameTestHelper helper) {
        TestFixtures.floor(helper, 1);
        BlockPos head = helper.absolutePos(TestFixtures.placeBed(helper, new BlockPos(4, 2, 4)));
        BlockPos origin = helper.absolutePos(GUARD);
        TestFixtures.check(helper, head.equals(GuardSleepGoal.findBed(helper.getLevel(), origin, 32)),
                "the guard should find the bed beside it");

        BlockState occupied = helper.getLevel().getBlockState(head).setValue(BedBlock.OCCUPIED, true);
        helper.getLevel().setBlock(head, occupied, 2);
        TestFixtures.check(helper, !head.equals(GuardSleepGoal.findBed(helper.getLevel(), origin, 32)),
                "a bed someone lies in is not free");
        helper.succeed();
    }
}
