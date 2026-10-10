package com.civillagers.gametest;

import com.civillagers.ai.CivCelebrateGoal;
import com.civillagers.ai.CivHideGoal;
import com.civillagers.ai.CivMeetGoal;
import com.civillagers.ai.CivPanicGoal;
import com.civillagers.ai.GuardRallyGoal;
import com.civillagers.entity.CivEntities;
import com.civillagers.entity.Guard;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BellBlockEntity;

/**
 * What the mod's villagers do as villagers do: civilians flee and hide, an armed guard rallies to the
 * bell, they meet in vanilla's hours, celebrate, and shake their heads.
 */
public final class VillagerLifeGameTest {
    private static final BlockPos GUARD = new BlockPos(1, 2, 1);

    @GameTest
    public void aGuardWithNoWeaponIsACivilian(GameTestHelper helper) {
        TestFixtures.floor(helper, 1);
        Guard guard = helper.spawnWithNoFreeWill(CivEntities.guard(), GUARD);
        guard.equipGear(new ItemStack(Items.IRON_SWORD));
        TestFixtures.check(helper, guard.isCombatant(), "a guard with a sword fights");
        guard.takeGear();
        TestFixtures.check(helper, !guard.isCombatant(), "a guard with no weapon is a civilian");
        Mob zombie = helper.spawnWithNoFreeWill(TestFixtures.zombie(), new BlockPos(5, 2, 5));
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        guard.setTarget(zombie);
        helper.succeedWhen(() -> TestFixtures.check(helper, guard.getTarget() == null, "a civilian should let go of its target"));
    }

    @GameTest
    public void aCivilianRunsFromAZombie(GameTestHelper helper) {
        TestFixtures.floor(helper, 1);
        Mob zombie = helper.spawnWithNoFreeWill(TestFixtures.zombie(), new BlockPos(4, 2, 1));
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        TestFixtures.check(helper, CivPanicGoal.fleeDistance(zombie) == 8, "a villager runs from a zombie within 8 blocks");
        Guard armed = helper.spawnWithNoFreeWill(CivEntities.guard(), new BlockPos(1, 2, 4));
        armed.equipGear(new ItemStack(Items.IRON_SWORD));
        TestFixtures.check(helper, !new CivPanicGoal(armed).canUse(), "an armed guard stands its ground");

        Guard civilian = helper.spawnWithNoFreeWill(CivEntities.guard(), GUARD);
        // The decision to run, tried each tick: a run takes a path from the budget every test shares, so
        // one tick's try may be refused while other tests run.
        helper.succeedWhen(() -> TestFixtures.check(helper,
                civilian.getNavigation().isInProgress() || new CivPanicGoal(civilian).canUse(),
                "a guard with no weapon should set off away from the zombie"));
    }

    @GameTest
    public void aRungBellSendsCiviliansHomeAndGuardsToTheBell(GameTestHelper helper) {
        TestFixtures.floor(helper, 1);
        BlockPos bellAt = new BlockPos(6, 2, 6);
        helper.setBlock(bellAt, Blocks.BELL);
        Guard armed = helper.spawnWithNoFreeWill(CivEntities.guard(), GUARD);
        armed.equipGear(new ItemStack(Items.IRON_SWORD));
        Guard civilian = helper.spawnWithNoFreeWill(CivEntities.guard(), new BlockPos(1, 2, 3));
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(bellAt)) instanceof BellBlockEntity bell)) {
            throw new IllegalStateException("no bell at " + bellAt);
        }
        bell.onHit(Direction.NORTH);
        helper.succeedWhen(() -> {
            TestFixtures.check(helper, helper.absolutePos(bellAt).equals(armed.alarmBell()), "a guard should hear the bell");
            TestFixtures.check(helper, new GuardRallyGoal(armed).canUse(), "an armed guard should run to the bell");
            TestFixtures.check(helper, CivHideGoal.wantsToHide(civilian) && !CivHideGoal.wantsToHide(armed),
                    "a guard with no weapon should want to hide, an armed one not");
        });
    }

    @GameTest
    public void aGuardShakesItsHeadAtAnEmptyHand(GameTestHelper helper) {
        TestFixtures.floor(helper, 1);
        Guard guard = helper.spawnWithNoFreeWill(CivEntities.guard(), GUARD);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        TestFixtures.interact(guard, player, InteractionHand.MAIN_HAND);
        TestFixtures.check(helper, guard.isUnhappy(), "a guard has nothing to trade, so it shakes its head");
        // Two seconds of shaking, run here at once: a test has fewer ticks than that.
        for (int i = 0; i < 40; i++) guard.aiStep();
        TestFixtures.check(helper, !guard.isUnhappy(), "and stops after two seconds");
        helper.succeed();
    }

    @GameTest
    public void villagersMeetInVanillasHours(GameTestHelper helper) {
        TestFixtures.check(helper, CivMeetGoal.isMeetingTime(9500), "9500 is in vanilla's meeting hours");
        TestFixtures.check(helper, !CivMeetGoal.isMeetingTime(3000) && !CivMeetGoal.isMeetingTime(11000),
                "the morning's work and the evening are not");
        helper.succeed();
    }

    @GameTest
    public void aCelebratingVillagerSendsUpAFirework(GameTestHelper helper) {
        TestFixtures.floor(helper, 1);
        Guard guard = helper.spawnWithNoFreeWill(CivEntities.guard(), GUARD);
        CivCelebrateGoal.launchFirework(helper.getLevel(), guard, guard.getRandom());
        TestFixtures.check(helper, !helper.getLevel().getEntitiesOfClass(FireworkRocketEntity.class,
                guard.getBoundingBox().inflate(3.0)).isEmpty(), "a firework should go up");
        helper.succeed();
    }
}
