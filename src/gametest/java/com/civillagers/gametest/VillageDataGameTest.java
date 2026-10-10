package com.civillagers.gametest;

import com.civillagers.Civillagers;
import com.civillagers.village.Village;
import com.civillagers.village.Villages;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

import java.util.UUID;

/** A village's saved data: created, written, read back unchanged, and kept per level. */
public final class VillageDataGameTest {
    @GameTest
    public void createsAVillageFromABell(GameTestHelper helper) {
        Villages villages = new Villages();
        Village village = villages.addAnchor(new BlockPos(100, 64, 100), 7L);
        TestFixtures.check(helper, village != null && villages.size() == 1, "a lone bell should found a village");
        TestFixtures.check(helper, village.center().equals(new BlockPos(100, 64, 100)), "the center should be the bell");
        TestFixtures.check(helper, village.radius() == Village.MIN_RADIUS, "one bell gives the smallest radius");
        TestFixtures.check(helper, village.level() == 1 && village.founded() == 7L, "a new village is level 1, founded now");
        TestFixtures.check(helper, villages.addAnchor(new BlockPos(100, 64, 100), 8L) == village,
                "adding a known bell again changes nothing");
        TestFixtures.check(helper, villages.size() == 1 && village.anchorCount() == 1, "still one village with one bell");
        helper.succeed();
    }

    @GameTest
    public void savesAndReloadsEveryField(GameTestHelper helper) {
        Villages villages = new Villages();
        Village first = villages.addAnchor(new BlockPos(0, 64, 0), 1L);
        villages.addAnchor(new BlockPos(40, 70, 0), 2L);
        Village second = villages.addAnchor(new BlockPos(1000, 64, 1000), 3L);
        UUID member = new UUID(0, 42);
        first.addMember(member);
        first.addToStockpile(Civillagers.id("planks"), 12);
        first.addToStockpile(Civillagers.id("stone"), 5);

        Tag saved = Villages.CODEC.encodeStart(NbtOps.INSTANCE, villages).getOrThrow();
        Villages loaded = Villages.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();

        TestFixtures.check(helper, loaded.size() == 2, "both villages should come back, not " + loaded.size());
        Village again = loaded.byId(first.id());
        TestFixtures.check(helper, again != null, "the first village should keep its id");
        TestFixtures.check(helper, again.anchorList().equals(first.anchorList()), "bells should survive a reload");
        TestFixtures.check(helper, again.center().equals(first.center()) && again.radius() == first.radius(),
                "center and radius follow from the bells");
        TestFixtures.check(helper, again.members().equals(first.members()), "members should survive a reload");
        TestFixtures.check(helper, again.stockpile(Civillagers.id("planks")) == 12
                && again.stockpile(Civillagers.id("stone")) == 5, "the stockpile should survive a reload");
        TestFixtures.check(helper, again.founded() == 1L, "the founding time should survive a reload");
        TestFixtures.check(helper, loaded.byId(second.id()) != null, "the second village should keep its id");

        Tag resaved = Villages.CODEC.encodeStart(NbtOps.INSTANCE, loaded).getOrThrow();
        TestFixtures.check(helper, resaved.equals(saved), "writing what was read should give the same data");

        Village third = loaded.addAnchor(new BlockPos(-5000, 64, 0), 4L);
        TestFixtures.check(helper, third.id() > second.id(), "ids are never reused after a reload");
        helper.succeed();
    }

    @GameTest
    public void stockpileNeverGoesNegative(GameTestHelper helper) {
        Village village = new Villages().addAnchor(new BlockPos(0, 64, 0), 0L);
        village.addToStockpile(Civillagers.id("bread"), 3);
        int taken = village.addToStockpile(Civillagers.id("bread"), -10);
        TestFixtures.check(helper, taken == -3, "only what is there can be taken, not " + taken);
        TestFixtures.check(helper, village.stockpile(Civillagers.id("bread")) == 0, "an empty stockpile entry is zero");
        TestFixtures.check(helper, village.stockpile().isEmpty(), "an emptied kind leaves the stockpile");
        helper.succeed();
    }

    @GameTest
    public void membersAreCapped(GameTestHelper helper) {
        Village village = new Villages().addAnchor(new BlockPos(0, 64, 0), 0L);
        for (int i = 0; i < Village.MAX_MEMBERS + 10; i++) village.addMember(new UUID(0, i));
        TestFixtures.check(helper, village.memberCount() == Village.MAX_MEMBERS, "members stop at the cap");
        helper.succeed();
    }

    @GameTest
    public void eachLevelHasOneSetOfVillages(GameTestHelper helper) {
        Villages data = Villages.get(helper.getLevel());
        TestFixtures.check(helper, data == Villages.get(helper.getLevel()), "a level should hand out the same data every time");
        helper.succeed();
    }
}
