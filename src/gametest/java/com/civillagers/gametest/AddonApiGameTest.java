package com.civillagers.gametest;

import com.civillagers.api.CivTags;
import com.civillagers.api.CivVillagerEvents;
import com.civillagers.api.GuardEvents;
import com.civillagers.api.Verdict;
import com.civillagers.entity.CivEntities;
import com.civillagers.entity.Guard;
import com.civillagers.entity.ZombieVillagers;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.UUID;

/**
 * The addon API in {@code com.civillagers.api}, used as an addon would: listeners registered once, the
 * weapon tag, and the hooks on turning and curing. A listener stays for the rest of the run, so each one
 * here only acts on what its own test sets up.
 */
public final class AddonApiGameTest {
    private static final BlockPos GUARD = new BlockPos(1, 2, 1);
    /** A mob with this name is no threat to a guard, by the test's {@link GuardEvents#IS_THREAT} listener. */
    private static final String FRIENDLY = "Friendly";

    private static boolean registered;
    /** What the listeners last saw, server thread only. */
    private static UUID lastWithGoals;
    /** The goal selector the goal listener was last handed; a guard's own is not public. Test runs only. */
    private static GoalSelector lastGoals;
    private static UUID lastTurned;
    private static UUID lastCured;

    /** A goal an addon might add; it never runs. */
    private static final class MarkerGoal extends Goal {
        @Override
        public boolean canUse() {
            return false;
        }
    }

    private static void registerOnce() {
        if (registered) return;
        registered = true;
        CivVillagerEvents.REGISTER_GOALS.register((villager, goals, targets) -> {
            goals.addGoal(9, new MarkerGoal());
            lastWithGoals = villager.getUUID();
            lastGoals = goals;
        });
        GuardEvents.IS_THREAT.register((guard, mob, ranged) ->
                mob.getCustomName() != null && FRIENDLY.equals(mob.getCustomName().getString()) ? Verdict.NO : Verdict.PASS);
        CivVillagerEvents.TURNED_ZOMBIE.register((villager, zombie) -> lastTurned = villager.getUUID());
        CivVillagerEvents.CURED.register((zombie, cured) -> lastCured = zombie.getUUID());
    }

    @GameTest
    public void anAddonAddsGoalsToEveryVillagerOfTheMod(GameTestHelper helper) {
        registerOnce();
        TestFixtures.floor(helper, 1);
        // With free will: taking it away clears every goal.
        Guard guard = helper.spawn(CivEntities.guard(), GUARD);
        TestFixtures.check(helper, guard.getUUID().equals(lastWithGoals), "a new guard should call the goal listeners");
        boolean found = false;
        for (WrappedGoal goal : lastGoals.getAvailableGoals()) found |= goal.getGoal() instanceof MarkerGoal;
        TestFixtures.check(helper, found, "the addon's goal should be among the guard's goals");
        helper.succeed();
    }

    @GameTest
    public void anAddonDecidesWhatAGuardGoesAfter(GameTestHelper helper) {
        registerOnce();
        TestFixtures.floor(helper, 1);
        Guard guard = helper.spawnWithNoFreeWill(CivEntities.guard(), GUARD);
        Mob zombie = helper.spawnWithNoFreeWill(TestFixtures.zombie(), new BlockPos(4, 2, 4));
        TestFixtures.check(helper, guard.goesAfter(zombie, false), "with no verdict, a guard goes after a zombie");
        zombie.setCustomName(Component.literal(FRIENDLY));
        TestFixtures.check(helper, !guard.goesAfter(zombie, false), "an addon's NO should keep the guard off it");
        TestFixtures.check(helper, Guard.isThreat(zombie, false), "the mod's own rule is unchanged");
        helper.succeed();
    }

    @GameTest
    public void aGuardTakesWhatTheWeaponTagHolds(GameTestHelper helper) {
        TestFixtures.floor(helper, 1);
        Guard guard = helper.spawnWithNoFreeWill(CivEntities.guard(), GUARD);
        for (ItemStack weapon : new ItemStack[] {
                new ItemStack(Items.IRON_SWORD), new ItemStack(Items.NETHERITE_AXE), new ItemStack(Items.BOW), new ItemStack(Items.CROSSBOW)}) {
            TestFixtures.check(helper, weapon.is(CivTags.GUARD_WEAPONS), weapon + " should be in civillagers:guard_weapons");
        }
        TestFixtures.check(helper, !new ItemStack(Items.TRIDENT).is(CivTags.GUARD_WEAPONS) && guard.gearSlot(new ItemStack(Items.TRIDENT)) == null,
                "a trident is not in the tag, so a guard does not take it");
        helper.succeed();
    }

    @GameTest
    public void turningAndCuringCallTheirListeners(GameTestHelper helper) {
        registerOnce();
        TestFixtures.floor(helper, 1);
        Guard guard = helper.spawnWithNoFreeWill(CivEntities.guard(), GUARD);
        UUID guardId = guard.getUUID();
        ZombieVillager zombie = ZombieVillagers.zombify(helper.getLevel(), guard);
        TestFixtures.check(helper, zombie != null && guardId.equals(lastTurned), "turning should call the listeners with the guard");
        TestFixtures.check(helper, ZombieVillagers.curesInto(zombie) == CivEntities.guard(), "the zombie should be cured into a guard");
        TestFixtures.check(helper, ZombieVillagers.cure(helper.getLevel(), zombie) != null && zombie.getUUID().equals(lastCured),
                "curing should call the listeners with the zombie");
        helper.succeed();
    }
}
