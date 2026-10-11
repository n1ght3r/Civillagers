package com.civillagers.gametest;

import com.civillagers.ai.guard.GuardHurtByTargetGoal;
import com.civillagers.ai.guard.GuardMeleeGoal;
import com.civillagers.ai.guard.GuardRetargetGoal;
import com.civillagers.entity.CivEntities;
import com.civillagers.entity.CivVillager;
import com.civillagers.entity.guard.Guard;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * A guard in a fight: chasing a mob into water and striking it there, giving up on one it cannot reach,
 * and turning on a nearer mob, not too often. A chase takes longer than a test's 20 ticks, so these tick
 * the mobs themselves ({@link TestFixtures#simulate}).
 */
public final class GuardCombatGameTest {
    /** Ticks a chase across the test's patch may take: twenty seconds, as a mob swims slowly. */
    private static final int CHASE_TICKS = 400;

    /** A guard with a sword that nothing near can kill, so a test is about where it goes, not whether it lives. */
    private static Guard armedGuard(GameTestHelper helper, BlockPos at) {
        Guard guard = helper.spawn(CivEntities.guard(), at);
        guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        // Effects and invulnerability differ by version; a deep pool of health does not.
        guard.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
        guard.setHealth(1000.0F);
        return guard;
    }

    /** A zombie that stands where it is put, with a helmet against the sun. */
    private static Mob stillZombie(GameTestHelper helper, BlockPos at) {
        Mob zombie = helper.spawnWithNoFreeWill(TestFixtures.zombie(), at);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        return zombie;
    }

    @GameTest
    public void aGuardInWaterSwimsToItsTargetAndStrikesItOnTheBottom(GameTestHelper helper) {
        TestFixtures.pool(helper, 1, 6, 3);
        Guard guard = armedGuard(helper, new BlockPos(1, 3, 1));
        Mob zombie = stillZombie(helper, new BlockPos(6, 1, 6));
        // The slowest pace a guard has: on 1.21.1 a slower swimmer crept along, its motion rounded away.
        guard.setSpeedVariant(-CivVillager.SPEED_VARIANT_RANGE);
        guard.setTarget(zombie);
        float health = zombie.getHealth();
        float[] turned = { 0.0F };
        float[] lastYaw = { guard.getYRot() };
        int ticks = TestFixtures.simulate(CHASE_TICKS, () -> zombie.getHealth() < health, () -> {
            turned[0] += Math.abs(Mth.wrapDegrees(guard.getYRot() - lastYaw[0]));
            lastYaw[0] = guard.getYRot();
        }, guard, zombie);
        TestFixtures.check(helper, ticks >= 0, "the guard should swim to the zombie on the bottom and strike it within "
                + CHASE_TICKS + " ticks; it got to " + guard.position() + ", the zombie is at " + zombie.position());
        TestFixtures.check(helper, turned[0] < 360.0F, "the guard should swim straight there, not spin: it turned " + (int) turned[0] + " degrees");
        helper.succeed();
    }

    @GameTest
    public void aGuardOnTheBankGoesIntoTheWaterAfterItsTarget(GameTestHelper helper) {
        TestFixtures.pool(helper, 4, 6, 3);
        Guard guard = armedGuard(helper, new BlockPos(1, 4, 3));
        Mob zombie = stillZombie(helper, new BlockPos(6, 1, 4));
        guard.setTarget(zombie);
        float health = zombie.getHealth();
        int ticks = TestFixtures.simulate(CHASE_TICKS, () -> zombie.getHealth() < health, () -> { }, guard, zombie);
        TestFixtures.check(helper, ticks >= 0, "the guard should leave the bank, swim to the zombie and strike it within "
                + CHASE_TICKS + " ticks; it got to " + guard.position() + ", the zombie is at " + zombie.position());
        helper.succeed();
    }

    @GameTest
    public void aGuardGivesUpOnATargetItCannotReach(GameTestHelper helper) {
        TestFixtures.floor(helper, 1);
        for (int y = 2; y <= 4; y++) helper.setBlock(new BlockPos(5, y, 5), Blocks.STONE);
        Guard guard = armedGuard(helper, new BlockPos(1, 2, 1));
        Mob zombie = stillZombie(helper, new BlockPos(5, 5, 5));
        guard.setTarget(zombie);
        int ticks = TestFixtures.simulate(GuardMeleeGoal.STALL_TICKS + CHASE_TICKS, () -> guard.getTarget() == null, () -> { }, guard, zombie);
        TestFixtures.check(helper, ticks >= GuardMeleeGoal.STALL_TICKS, "the guard should try for a while before it gives up, not after " + ticks + " ticks");
        TestFixtures.check(helper, ticks >= 0 && guard.gaveUpOn(zombie), "the guard should give up on a zombie on a pillar it cannot climb");
        TestFixtures.check(helper, guard.findThreat() == null, "and not pick it again at once");
        guard.tickCount += Guard.GIVE_UP_TICKS;
        TestFixtures.check(helper, guard.findThreat() == zombie, "but it is a threat again some time later");
        helper.succeed();
    }

    @GameTest
    public void aGuardTurnsOnANearerMobHittingItButNotTooOften(GameTestHelper helper) {
        TestFixtures.floor(helper, 1);
        Guard guard = helper.spawnWithNoFreeWill(CivEntities.guard(), new BlockPos(1, 2, 1));
        guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        Mob archer = helper.spawnWithNoFreeWill(TestFixtures.skeleton(), new BlockPos(6, 2, 6));
        archer.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        archer.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        Mob vindicator = helper.spawnWithNoFreeWill(TestFixtures.vindicator(), new BlockPos(2, 2, 1));
        GuardRetargetGoal retarget = new GuardRetargetGoal(guard);
        GuardHurtByTargetGoal hurtBy = new GuardHurtByTargetGoal(guard, Guard.class, Villager.class, IronGolem.class);

        guard.setTarget(archer);
        guard.setLastHurtByMob(vindicator);
        retarget.canUse();
        TestFixtures.check(helper, guard.getTarget() == archer && !hurtBy.canUse(), "a guard keeps a target it just picked for a moment");

        guard.tickCount += Guard.RETARGET_COOLDOWN;
        guard.setLastHurtByMob(vindicator);
        retarget.canUse();
        TestFixtures.check(helper, guard.getTarget() == vindicator, "then it turns on the vindicator hitting it, away from the archer far off");
        TestFixtures.check(helper, !guard.mayRetarget(), "and keeps to it for a while");

        guard.tickCount += Guard.RETARGET_COOLDOWN;
        guard.setLastHurtByMob(archer);
        TestFixtures.check(helper, !hurtBy.canUse(), "an arrow from the archer far off does not turn it back");
        retarget.canUse();
        TestFixtures.check(helper, guard.getTarget() == vindicator, "nor does a look round");

        TestFixtures.check(helper, !guard.prefers(archer, vindicator), "a farther mob is never preferred");
        archer.setPos(vindicator.getX() + 0.2, vindicator.getY(), vindicator.getZ() + 0.2);
        TestFixtures.check(helper, !guard.prefers(archer, vindicator), "nor one at much the same distance: two mobs never trade places");
        helper.succeed();
    }

    @GameTest
    public void aFightingGuardTurnsOnTheVindicatorBesideIt(GameTestHelper helper) {
        TestFixtures.floor(helper, 1);
        Guard guard = armedGuard(helper, new BlockPos(1, 2, 1));
        Mob archer = helper.spawnWithNoFreeWill(TestFixtures.skeleton(), new BlockPos(6, 2, 6));
        archer.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        Mob vindicator = helper.spawn(TestFixtures.vindicator(), new BlockPos(3, 2, 1));
        guard.setTarget(archer);
        int ticks = TestFixtures.simulate(CHASE_TICKS, () -> guard.getTarget() == vindicator, () -> { }, guard, vindicator);
        TestFixtures.check(helper, ticks >= 0, "the guard should turn from the archer far off to the vindicator beside it");
        TestFixtures.check(helper, ticks >= Guard.RETARGET_COOLDOWN, "but not the moment it picked the archer: after " + ticks + " ticks");
        helper.succeed();
    }
}
