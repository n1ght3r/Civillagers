package com.civillagers.gametest;

import com.civillagers.village.Village;
import com.civillagers.village.VillageDetector;
import com.civillagers.village.VillageScheduler;
import com.civillagers.village.VillageTicker;
import com.civillagers.village.Villages;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Villages found from real bells and beds in the world, and updated from them. Each test uses its own
 * {@link Villages}, never the level's, so tests cannot see each other's villages.
 */
public final class VillageDetectionGameTest {
    private static final BlockPos BELL = new BlockPos(1, 2, 1);
    private static final BlockPos BED_FOOT = new BlockPos(5, 2, 5);

    @GameTest
    public void aBellWithABedFoundsAVillage(GameTestHelper helper) {
        placeBell(helper);
        placeBed(helper);
        Villages villages = new Villages();
        BlockPos bell = helper.absolutePos(BELL);
        int added = VillageDetector.discover(helper.getLevel(), villages, bell, 1, 0L);
        TestFixtures.check(helper, villages.byAnchor(bell.asLong()) != null, "the bell should found a village");
        TestFixtures.check(helper, added >= 1, "discovery should report the bell");
        TestFixtures.check(helper, villages.villageAt(bell) == villages.byAnchor(bell.asLong()),
                "the village should be found at its bell");
        int before = villages.size();
        VillageDetector.discover(helper.getLevel(), villages, bell, 1, 0L);
        TestFixtures.check(helper, villages.size() == before, "a second scan adds nothing new");
        helper.succeed();
    }

    @GameTest
    public void aBellAloneIsNoVillage(GameTestHelper helper) {
        placeBell(helper);
        Villages villages = new Villages();
        BlockPos bell = helper.absolutePos(BELL);
        // Only this bell is looked at: a neighbouring test's bed could be within reach of a wider scan.
        if (!bedWithinReach(helper, bell)) {
            VillageDetector.discover(helper.getLevel(), villages, bell, 0, 0L);
            TestFixtures.check(helper, villages.byAnchor(bell.asLong()) == null, "a bell with no bed nearby founds nothing");
        }
        helper.succeed();
    }

    @GameTest
    public void theFirstUpdateCountsBeds(GameTestHelper helper) {
        placeBell(helper);
        placeBed(helper);
        Villages villages = new Villages();
        Village village = villages.addAnchor(helper.absolutePos(BELL), 0L);
        // Ticking at the village's slot runs its first update, which recounts its points of interest.
        VillageTicker.tickLevel(helper.getLevel(), villages, VillageScheduler.slot(village.id()));
        TestFixtures.check(helper, village.beds() >= 1, "the bed should be counted, got " + village.beds());
        helper.succeed();
    }

    @GameTest
    public void aBrokenBellDisbandsTheVillage(GameTestHelper helper) {
        placeBell(helper);
        Villages villages = new Villages();
        Village village = villages.addAnchor(helper.absolutePos(BELL), 0L);
        helper.setBlock(BELL, Blocks.AIR);
        VillageTicker.INSTANCE.update(helper.getLevel(), villages, village, 0L);
        TestFixtures.check(helper, villages.isEmpty(), "a village whose only bell is gone is disbanded");
        helper.succeed();
    }

    @GameTest
    public void aBellInAnUnloadedChunkIsKept(GameTestHelper helper) {
        Villages villages = new Villages();
        Village village = villages.addAnchor(new BlockPos(29_000_000, 64, 29_000_000), 0L);
        TestFixtures.check(helper, !VillageTicker.INSTANCE.canUpdate(helper.getLevel(), village),
                "a village in unloaded chunks is not updated");
        VillageTicker.INSTANCE.update(helper.getLevel(), villages, village, 0L);
        TestFixtures.check(helper, villages.size() == 1, "a bell that cannot be seen is not assumed gone");
        helper.succeed();
    }

    private static void placeBell(GameTestHelper helper) {
        helper.setBlock(BELL.below(), Blocks.STONE);
        helper.setBlock(BELL, Blocks.BELL);
    }

    private static void placeBed(GameTestHelper helper) {
        BlockState bed = TestFixtures.anyBed().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        helper.setBlock(BED_FOOT.below(), Blocks.STONE);
        helper.setBlock(BED_FOOT.north().below(), Blocks.STONE);
        helper.setBlock(BED_FOOT.north(), bed.setValue(BedBlock.PART, BedPart.HEAD));
        helper.setBlock(BED_FOOT, bed.setValue(BedBlock.PART, BedPart.FOOT));
    }

    private static boolean bedWithinReach(GameTestHelper helper, BlockPos bell) {
        return helper.getLevel().getPoiManager().getCountInRange(
                holder -> holder.is(net.minecraft.world.entity.ai.village.poi.PoiTypes.HOME),
                bell, Village.LINK_DISTANCE, net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY) > 0;
    }
}
