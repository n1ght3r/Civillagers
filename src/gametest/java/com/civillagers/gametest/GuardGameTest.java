package com.civillagers.gametest;

import com.civillagers.ai.GuardCombatPath;
import com.civillagers.ai.GuardEnemies;
import com.civillagers.ai.GuardPatrolGoal;
import com.civillagers.ai.GuardShieldGoal;
import com.civillagers.ai.GuardSleepGoal;
import com.civillagers.ai.GuardTargetGoal;
import com.civillagers.entity.CivEntities;
import com.civillagers.entity.Guard;
import com.civillagers.entity.ZombieVillagers;
import com.civillagers.platform.Vanilla;
import com.civillagers.village.Village;
import com.civillagers.village.Villages;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Guards: their clothes, their gear, turning into zombies and being cured, how often they look for
 * targets, combat, patrol points and beds, idling with nobody near, path searches in a fight, the shield,
 * the crossbow, and the mobs that hunt them.
 */
public final class GuardGameTest {
    private static final BlockPos GUARD = new BlockPos(1, 2, 1);

    @GameTest
    public void aGuardTurnsWithAVillagersOdds(GameTestHelper helper) {
        RandomSource random = RandomSource.create(1L);
        for (int i = 0; i < 20; i++) {
            TestFixtures.check(helper, !ZombieVillagers.turns(Difficulty.PEACEFUL, random) && !ZombieVillagers.turns(Difficulty.EASY, random),
                    "a guard never turns on Peaceful or Easy");
            TestFixtures.check(helper, ZombieVillagers.turns(Difficulty.HARD, random), "a guard always turns on Hard");
        }
        int turned = 0;
        for (int i = 0; i < 100; i++) if (ZombieVillagers.turns(Difficulty.NORMAL, random)) turned++;
        TestFixtures.check(helper, turned > 20 && turned < 80, "a guard turns about half the time on Normal, not " + turned + " in 100");
        helper.succeed();
    }

    @GameTest
    public void aZombieGuardKeepsItsGearAndIsCuredBackToAGuard(GameTestHelper helper) {
        TestFixtures.floor(helper, 1);
        Guard guard = helper.spawnWithNoFreeWill(CivEntities.guard(), GUARD);
        guard.setVillagerType("minecraft:snow");
        guard.equipGear(new ItemStack(Items.DIAMOND_SWORD));
        guard.equipGear(new ItemStack(Items.IRON_HELMET));
        guard.setCustomName(Component.literal("Gregor"));

        ZombieVillager zombie = ZombieVillagers.zombify(helper.getLevel(), guard);
        TestFixtures.check(helper, zombie != null && guard.isRemoved(), "the guard should turn into a zombie villager");
        TestFixtures.check(helper, zombie.entityTags().contains(ZombieVillagers.tagFor(CivEntities.guard())), "the zombie should remember it was a guard");
        TestFixtures.check(helper, zombie.getMainHandItem().is(Items.DIAMOND_SWORD) && zombie.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET),
                "the zombie should keep the guard's gear");
        TestFixtures.check(helper, Vanilla.villagerTypeOf(zombie, "").equals("minecraft:snow"), "the zombie should keep the guard's clothes");
        TestFixtures.check(helper, zombie.isPersistenceRequired(), "a zombie guard never despawns with the gear it carries");

        // Cured as vanilla cures a zombie villager: weakness, then a golden apple.
        zombie.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 600));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLDEN_APPLE));
        TestFixtures.interact(zombie, player, InteractionHand.MAIN_HAND);
        TestFixtures.check(helper, zombie.isConverting(), "a golden apple should start the cure");
        TestFixtures.finishCureSoon(zombie);

        helper.succeedWhen(() -> {
            List<Guard> guards = helper.getLevel().getEntitiesOfClass(Guard.class, zombie.getBoundingBox().inflate(2.0),
                    found -> found.getCustomName() != null && found.getCustomName().getString().equals("Gregor"));
            TestFixtures.check(helper, zombie.isRemoved() && guards.size() == 1, "the cured zombie should come back a guard");
            Guard cured = guards.get(0);
            TestFixtures.check(helper, cured.getMainHandItem().is(Items.DIAMOND_SWORD) && cured.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET),
                    "the cured guard should keep its gear");
            TestFixtures.check(helper, cured.villagerType().equals("minecraft:snow"), "the cured guard should keep its clothes");
            TestFixtures.check(helper, !cured.entityTags().contains(ZombieVillagers.tagFor(CivEntities.guard())), "the cured guard is no zombie any more");
        });
    }

    @GameTest
    public void aGuardAZombieKillsOnHardRisesAsAZombie(GameTestHelper helper) {
        TestFixtures.floor(helper, 1);
        Guard guard = helper.spawnWithNoFreeWill(CivEntities.guard(), GUARD);
        guard.equipGear(new ItemStack(Items.IRON_SWORD));
        Mob zombie = helper.spawnWithNoFreeWill(TestFixtures.zombie(), new BlockPos(3, 2, 3));
        // The difficulty is the server's, so it is changed and put back at once, before any other test ticks.
        net.minecraft.server.MinecraftServer server = helper.getLevel().getServer();
        Difficulty before = helper.getLevel().getDifficulty();
        server.setDifficulty(Difficulty.HARD, true);
        boolean drops;
        try {
            drops = TestFixtures.zombieKills(zombie, guard);
        } finally {
            server.setDifficulty(before, true);
        }
        TestFixtures.check(helper, !drops && guard.isRemoved(), "on Hard a guard a zombie kills should turn, dropping nothing");
        List<ZombieVillager> risen = helper.getLevel().getEntitiesOfClass(ZombieVillager.class, guard.getBoundingBox().inflate(1.0),
                found -> found.entityTags().contains(ZombieVillagers.tagFor(CivEntities.guard())));
        TestFixtures.check(helper, risen.size() == 1 && risen.get(0).getMainHandItem().is(Items.IRON_SWORD),
                "the guard should rise as a zombie villager with its sword");
        helper.succeed();
    }

    @GameTest
    public void anOrdinaryZombieVillagerIsLeftToVanilla(GameTestHelper helper) {
        TestFixtures.floor(helper, 1);
        ZombieVillager zombie = helper.spawnWithNoFreeWill(Vanilla.zombieVillagerType(), GUARD);
        TestFixtures.check(helper, ZombieVillagers.cure(helper.getLevel(), zombie) == null && !zombie.isRemoved(),
                "a zombie villager that was never a guard is cured into a villager, as in vanilla");
        helper.succeed();
    }

    @GameTest
    public void aNewGuardCarriesASword(GameTestHelper helper) {
        TestFixtures.floor(helper, 1);
        Guard guard = helper.spawnWithNoFreeWill(CivEntities.guard(), GUARD);
        guard.finalizeSpawn(helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(guard.blockPosition()),
                EntitySpawnReason.MOB_SUMMONED, null);
        TestFixtures.check(helper, guard.getMainHandItem().is(Items.IRON_SWORD), "a new guard should hold an iron sword");
        TestFixtures.check(helper, guard.getMaxHealth() == 20.0F, "a guard has a villager's 20 health, not " + guard.getMaxHealth());
        TestFixtures.check(helper, !guard.removeWhenFarAway(1000.0), "guards never despawn");
        helper.succeed();
    }

    @GameTest
    public void aGuardWearsTheClothesOfItsBiome(GameTestHelper helper) {
        TestFixtures.floor(helper, 1);
        Guard guard = helper.spawnWithNoFreeWill(CivEntities.guard(), GUARD);
        guard.finalizeSpawn(helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(guard.blockPosition()),
                EntitySpawnReason.MOB_SUMMONED, null);
        String biome = Vanilla.villagerTypeAt(helper.getLevel(), guard.blockPosition());
        TestFixtures.check(helper, guard.villagerType().equals(biome),
                "a new guard should wear the clothes of its biome, " + biome + ", not " + guard.villagerType());

        guard.setVillagerType("minecraft:snow");
        Map<String, String> saved = new HashMap<>();
        guard.saveExtra(saved::put);
        Guard reloaded = helper.spawnWithNoFreeWill(CivEntities.guard(), GUARD);
        reloaded.loadExtra(saved::getOrDefault);
        TestFixtures.check(helper, reloaded.villagerType().equals("minecraft:snow"), "the villager type should survive a reload");

        reloaded.setVillagerType("not an id!");
        TestFixtures.check(helper, reloaded.villagerType().equals("minecraft:snow"), "a guard ignores a villager type that is not an id");
        Guard old = helper.spawnWithNoFreeWill(CivEntities.guard(), GUARD);
        old.loadExtra((key, fallback) -> fallback);
        TestFixtures.check(helper, old.villagerType().equals(Guard.DEFAULT_VILLAGER_TYPE),
                "a guard saved without a villager type should wear the plains' clothes");
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
        // Armed: a guard with no weapon looks for nothing.
        guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        GuardTargetGoal goal = new GuardTargetGoal(guard);
        // The test world has no player, so the guard idles and looks less often.
        int start = guard.tickCount;
        checkScanRate(helper, guard, goal, start, GuardTargetGoal.IDLE_SCAN_INTERVAL, "with nobody near");
        ServerPlayer player = TestFixtures.playerAt(helper, guard);
        try {
            // Far enough on that the guard looks for players again.
            checkScanRate(helper, guard, goal, start + 400, GuardTargetGoal.SCAN_INTERVAL, "with a player near");
        } finally {
            TestFixtures.removePlayer(helper, player);
        }
        helper.succeed();
    }

    /** Runs {@code goal} over 200 ticks from {@code start} and checks it scanned about once every {@code interval}. */
    private static void checkScanRate(GameTestHelper helper, Guard guard, GuardTargetGoal goal, int start, int interval, String when) {
        int ticks = 200;
        int first = goal.scans();
        int lastScan = -interval;
        for (int t = 0; t < ticks; t++) {
            guard.tickCount = start + t;
            int before = goal.scans();
            goal.canUse();
            if (goal.scans() == before) continue;
            TestFixtures.check(helper, t - lastScan >= interval,
                    "two scans " + (t - lastScan) + " ticks apart " + when + ", under " + interval);
            lastScan = t;
        }
        int scans = goal.scans() - first;
        TestFixtures.check(helper, scans <= ticks / interval + 1, scans + " scans in " + ticks + " ticks " + when + " is too many");
        TestFixtures.check(helper, scans >= ticks / (2 * interval), "a guard still looks around " + when + ": " + scans + " scans");
    }

    @GameTest
    public void aGuardIdlesWithNobodyNear(GameTestHelper helper) {
        TestFixtures.floor(helper, 1);
        BlockPos bell = helper.absolutePos(new BlockPos(4, 2, 4));
        helper.setBlock(new BlockPos(4, 2, 4), Blocks.BELL);
        Villages villages = Villages.get(helper.getLevel());
        Village village = villages.addAnchor(bell, 0L);
        try {
            Guard guard = helper.spawnWithNoFreeWill(CivEntities.guard(), GUARD);
            GuardPatrolGoal patrol = new GuardPatrolGoal(guard);
            guard.tickCount += 1000;
            TestFixtures.check(helper, !guard.playerNear(), "the test world has no player");
            for (int i = 0; i < 20; i++) {
                TestFixtures.check(helper, !patrol.canUse(), "a guard nobody sees stands at its post");
                guard.tickCount += 40;
            }
            ServerPlayer player = TestFixtures.playerAt(helper, guard);
            try {
                guard.tickCount += 100;
                TestFixtures.check(helper, guard.playerNear(), "a player beside the guard is near");
            } finally {
                TestFixtures.removePlayer(helper, player);
            }
        } finally {
            villages.removeAnchor(village, bell.asLong(), 0L);
        }
        helper.succeed();
    }

    @GameTest
    public void aFightingGuardSearchesOnlyWhenItsTargetMoves(GameTestHelper helper) {
        TestFixtures.floor(helper, 1);
        Guard guard = helper.spawnWithNoFreeWill(CivEntities.guard(), GUARD);
        // A mob that has not ticked yet has not landed, and a mob in the air finds no path.
        guard.setOnGround(true);
        Mob zombie = helper.spawnWithNoFreeWill(TestFixtures.zombie(), new BlockPos(6, 2, 1));
        GuardCombatPath path = new GuardCombatPath(guard);
        TestFixtures.check(helper, path.follow(zombie, 0.75), "the first step of a fight searches a path");
        int calls = 3 * GuardCombatPath.MIN_INTERVAL;
        for (int i = 0; i < calls; i++) path.follow(zombie, 0.75);
        TestFixtures.check(helper, path.searches() == 1, "a target standing still needs no new path, not " + path.searches());

        zombie.setPos(zombie.getX(), zombie.getY(), zombie.getZ() + 1.0);
        for (int i = 0; i < calls; i++) path.follow(zombie, 0.75);
        TestFixtures.check(helper, path.searches() == 1, "a step of one block needs none either, not " + path.searches());

        zombie.setPos(zombie.getX(), zombie.getY(), zombie.getZ() + 3.0);
        for (int i = 0; i < calls; i++) path.follow(zombie, 0.75);
        TestFixtures.check(helper, path.searches() == 2, "a target that moved " + (int) (GuardCombatPath.MOVED + 2)
                + " blocks gets one new path, not " + (path.searches() - 1));
        helper.succeed();
    }

    @GameTest
    public void aGuardRaisesItsShieldAgainstArchersAndCreepers(GameTestHelper helper) {
        TestFixtures.floor(helper, 1);
        Guard guard = helper.spawnWithNoFreeWill(CivEntities.guard(), GUARD);
        guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        guard.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        GuardShieldGoal goal = new GuardShieldGoal(guard);
        TestFixtures.check(helper, !goal.canUse(), "nothing calls for the shield yet");

        Mob skeleton = helper.spawnWithNoFreeWill(TestFixtures.skeleton(), new BlockPos(6, 2, 1));
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        guard.setTarget(skeleton);
        TestFixtures.check(helper, goal.canUse(), "an archer out of reach calls for the shield");
        goal.start();
        TestFixtures.check(helper, guard.isUsingItem() && guard.getUsedItemHand() == InteractionHand.OFF_HAND,
                "the guard raises the shield in its off hand");
        skeleton.setPos(guard.getX() + 1.0, guard.getY(), guard.getZ());
        TestFixtures.check(helper, !goal.canContinueToUse(), "within reach the shield comes down, so the guard can strike");
        goal.stop();
        TestFixtures.check(helper, !guard.isUsingItem(), "and the guard has lowered it");
        guard.setTarget(null);
        skeleton.discard();

        Creeper creeper = (Creeper) helper.spawnWithNoFreeWill(TestFixtures.creeper(), new BlockPos(4, 2, 1));
        try {
            guard.tickCount += GuardShieldGoal.CREEPER_CHECK_INTERVAL;
            TestFixtures.check(helper, !goal.canUse(), "a calm creeper calls for nothing");
            creeper.setSwellDir(1);
            guard.tickCount += GuardShieldGoal.CREEPER_CHECK_INTERVAL;
            TestFixtures.check(helper, goal.canUse(), "a creeper about to blow up calls for the shield");
            guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            TestFixtures.check(helper, !goal.canUse(), "a guard with a bow keeps its hands for the bow");
        } finally {
            // Before it ticks and goes off.
            creeper.discard();
        }
        helper.succeed();
    }

    @GameTest
    public void aGuardTakesACrossbowAndGivesItBackUnloaded(GameTestHelper helper) {
        TestFixtures.floor(helper, 1);
        Guard guard = helper.spawnWithNoFreeWill(CivEntities.guard(), GUARD);
        ItemStack loaded = TestFixtures.loadedCrossbow();
        TestFixtures.check(helper, guard.gearSlot(loaded) == EquipmentSlot.MAINHAND, "a crossbow is a weapon");
        guard.equipGear(loaded);
        TestFixtures.check(helper, guard.holdsCrossbow() && guard.holdsRangedWeapon(), "the guard holds a crossbow");
        TestFixtures.check(helper, guard.getProjectile(guard.getMainHandItem()).is(Items.ARROW), "and needs no arrows of its own");
        ItemStack back = guard.takeGear();
        TestFixtures.check(helper, back.is(Items.CROSSBOW) && !CrossbowItem.isCharged(back), "the crossbow comes back unloaded");
        helper.succeed();
    }

    @GameTest
    public void aGuardShootsALoadedCrossbow(GameTestHelper helper) {
        TestFixtures.floor(helper, 1);
        Guard guard = helper.spawn(CivEntities.guard(), GUARD);
        guard.setItemSlot(EquipmentSlot.MAINHAND, TestFixtures.loadedCrossbow());
        Mob zombie = helper.spawnWithNoFreeWill(TestFixtures.zombie(), new BlockPos(6, 2, 6));
        // A helmet keeps the zombie from burning before the guard shoots.
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        guard.setTarget(zombie);
        helper.succeedWhen(() -> TestFixtures.check(helper, !CrossbowItem.isCharged(guard.getMainHandItem()),
                "the guard should shoot the bolt in its crossbow"));
    }

    @GameTest
    public void zombiesAndRaidersHuntGuards(GameTestHelper helper) {
        TestFixtures.floor(helper, 1);
        TestFixtures.check(helper, !GuardEnemies.huntsGuards(helper.spawnWithNoFreeWill(TestFixtures.zombifiedPiglin(), new BlockPos(7, 2, 7))),
                "zombified piglins are neutral and leave guards alone");
        Guard guard = helper.spawnWithNoFreeWill(CivEntities.guard(), new BlockPos(4, 2, 4));
        // A target goal looks on a random tick, so several of each make the test certain within its time.
        Mob[] zombies = new Mob[4];
        Mob[] vindicators = new Mob[4];
        for (int i = 0; i < 4; i++) {
            zombies[i] = helper.spawn(TestFixtures.zombie(), new BlockPos(1 + 2 * i, 2, 1));
            zombies[i].setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            vindicators[i] = helper.spawn(TestFixtures.vindicator(), new BlockPos(1 + 2 * i, 2, 6));
        }
        TestFixtures.check(helper, GuardEnemies.huntsGuards(zombies[0]) && GuardEnemies.huntsGuards(vindicators[0]),
                "zombies and illagers hunt guards");
        helper.succeedWhen(() -> {
            TestFixtures.check(helper, targets(zombies, guard), "a zombie should go after the guard");
            TestFixtures.check(helper, targets(vindicators, guard), "a vindicator should go after the guard");
        });
    }

    private static boolean targets(Mob[] mobs, Guard guard) {
        for (Mob mob : mobs) if (mob.getTarget() == guard) return true;
        return false;
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
