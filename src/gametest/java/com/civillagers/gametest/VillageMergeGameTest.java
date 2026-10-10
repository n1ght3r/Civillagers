package com.civillagers.gametest;

import com.civillagers.village.Village;
import com.civillagers.village.Villages;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.UUID;

/** Bells that link up make one village; a village whose bells no longer link up splits. */
public final class VillageMergeGameTest {
    private static final BlockPos WEST = new BlockPos(0, 64, 0);
    private static final BlockPos EAST = new BlockPos(120, 64, 0);
    /** Within {@link Village#LINK_DISTANCE} of both. */
    private static final BlockPos MIDDLE = new BlockPos(60, 64, 0);

    @GameTest
    public void distantBellsAreSeparateVillages(GameTestHelper helper) {
        Villages villages = new Villages();
        Village west = villages.addAnchor(WEST, 0L);
        Village east = villages.addAnchor(EAST, 0L);
        TestFixtures.check(helper, villages.size() == 2 && west != east, "bells 120 blocks apart are two villages");
        helper.succeed();
    }

    @GameTest
    public void aBellBetweenTwoVillagesMergesThem(GameTestHelper helper) {
        Villages villages = new Villages();
        Village west = villages.addAnchor(WEST, 0L);
        Village east = villages.addAnchor(EAST, 0L);
        east.addMember(new UUID(1, 1));
        Village merged = villages.addAnchor(MIDDLE, 0L);
        TestFixtures.check(helper, villages.size() == 1, "the bridging bell should merge the villages");
        TestFixtures.check(helper, merged == west, "the oldest village survives a merge");
        TestFixtures.check(helper, merged.anchorCount() == 3, "the merged village has all three bells");
        TestFixtures.check(helper, merged.members().contains(new UUID(1, 1)), "members move with the merge");
        TestFixtures.check(helper, villages.byId(east.id()) == null, "the absorbed village is gone");
        TestFixtures.check(helper, merged.center().equals(MIDDLE), "the center is the middle of the bells");
        TestFixtures.check(helper, villages.byAnchor(EAST.asLong()) == merged, "the absorbed bell now belongs to the merged village");
        helper.succeed();
    }

    @GameTest
    public void losingTheBridgingBellSplitsTheVillage(GameTestHelper helper) {
        Villages villages = new Villages();
        Village village = villages.addAnchor(WEST, 0L);
        villages.addAnchor(new BlockPos(10, 64, 0), 0L);
        villages.addAnchor(MIDDLE, 0L);
        villages.addAnchor(EAST, 0L);
        TestFixtures.check(helper, villages.size() == 1, "four linked bells are one village");

        villages.removeAnchor(village, MIDDLE.asLong(), 5L);
        TestFixtures.check(helper, villages.size() == 2, "without the middle bell the village splits, into " + villages.size());
        TestFixtures.check(helper, villages.byId(village.id()) == village && village.anchorCount() == 2,
                "the larger group keeps the village");
        Village split = villages.byAnchor(EAST.asLong());
        TestFixtures.check(helper, split != null && split != village && split.founded() == 5L,
                "the cut-off bell founds a new village");
        helper.succeed();
    }

    @GameTest
    public void losingTheLastBellDisbands(GameTestHelper helper) {
        Villages villages = new Villages();
        Village village = villages.addAnchor(WEST, 0L);
        villages.removeAnchor(village, WEST.asLong(), 0L);
        TestFixtures.check(helper, villages.isEmpty(), "a village with no bell is no village");
        TestFixtures.check(helper, villages.villageAt(WEST) == null, "and the index forgets it");
        helper.succeed();
    }

    @GameTest
    public void theIndexFindsTheVillageAtAPosition(GameTestHelper helper) {
        Villages villages = new Villages();
        // A wide village: center x=30, radius 62, reaching x=92.
        Village wide = villages.addAnchor(WEST, 0L);
        villages.addAnchor(MIDDLE, 0L);
        // A small one high above, too far from the wide one's bells in 3D to link: center x=100, reaching x=68.
        Village small = villages.addAnchor(new BlockPos(100, 130, 0), 0L);
        TestFixtures.check(helper, villages.size() == 2, "two villages, not " + villages.size());
        TestFixtures.check(helper, villages.villageAt(new BlockPos(-20, 70, 5)) == wide, "inside the wide village only");
        TestFixtures.check(helper, villages.villageAt(new BlockPos(120, 70, 5)) == small, "inside the small village only");
        // Chunk x=4 belongs to the small village, whose center is nearer, but this corner of it is only
        // inside the wide one: the lookup has to fall back to the chunk's other villages.
        BlockPos corner = new BlockPos(64, 64, 15);
        TestFixtures.check(helper, villages.villageAt(corner) == wide, "a shared chunk still answers with the right village");
        TestFixtures.check(helper, villages.villageAt(new BlockPos(500, 64, 500)) == null, "far away is no village");
        helper.succeed();
    }
}
