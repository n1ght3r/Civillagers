package com.civillagers.ai.villager;

import com.civillagers.ai.Ground;
import com.civillagers.ai.GuardPathBudget;
import com.civillagers.entity.CivVillager;
import com.civillagers.platform.Vanilla;
import com.civillagers.village.Village;
import com.civillagers.village.Villages;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.EnumSet;
import java.util.List;
import java.util.function.Predicate;

/**
 * In vanilla's meeting hours ({@link #MEET_START} to {@link #MEET_END}, late morning) a villager of the mod
 * now and then walks to its village's bell, as villagers gather there, and stands a while talking: it
 * turns to the villager or villager of the mod nearest it. Each turns only its own head: two of the mod's
 * beside each other face each other because each is the other's nearest, and a third talking to one of
 * them never pulls that one's head away, which would swing it between the two every tick.
 *
 * <p>Meeting is for players to see, so it only happens with one near ({@link CivVillager#playerNear()}). It
 * decides at most once every {@link #CHECK_INTERVAL} ticks, walks one path from {@link GuardPathBudget},
 * and looks for someone to talk to at most once every {@link #TALK_INTERVAL} ticks, in a small box.
 */
public final class CivMeetGoal extends Goal {
    /** Vanilla's meeting hours for villagers, in ticks of the day. */
    public static final int MEET_START = 9000;
    public static final int MEET_END = 11000;
    static final int CHECK_INTERVAL = 200;
    /** Farther than this from the bell, in blocks, a villager does not walk over: a path that long costs too much. */
    private static final int MAX_WALK = 32;
    /** Ticks it stands at the bell: at least the first, plus up to the second. */
    private static final int STAY = 300;
    private static final int STAY_JITTER = 300;
    private static final int TALK_INTERVAL = 60;
    private static final double TALK_RANGE = 5.0;
    private static final double SPEED = 0.5;

    /** Who a villager of the mod talks to: villagers, wandering traders, and its own kind. No capture. */
    private static final Predicate<LivingEntity> TALKER = entity ->
            entity.isAlive() && (entity instanceof AbstractVillager || entity instanceof CivVillager);

    private final CivVillager villager;
    private final BlockPos.MutableBlockPos point = new BlockPos.MutableBlockPos();
    private int nextCheck;
    private int stayUntil;
    private int nextTalk;
    private boolean arrived;
    /** Who it is talking to; held only while this goal runs, as vanilla's look goals hold theirs. */
    private LivingEntity partner;

    public CivMeetGoal(CivVillager villager) {
        this.villager = villager;
        this.nextCheck = villager.getRandom().nextInt(CHECK_INTERVAL);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /** Whether {@code timeOfDay} (0 to 23999) is in vanilla's meeting hours. */
    public static boolean isMeetingTime(int timeOfDay) {
        return timeOfDay >= MEET_START && timeOfDay < MEET_END;
    }

    private boolean free() {
        return villager.getTarget() == null && !villager.isSleeping() && villager.alarmBell() == null
                && villager.raidCenter() == null;
    }

    @Override
    public boolean canUse() {
        if (villager.tickCount < nextCheck) return false;
        nextCheck = villager.tickCount + CHECK_INTERVAL;
        ServerLevel level = villager.serverLevel();
        if (level == null || !isMeetingTime(Vanilla.timeOfDay(level)) || !free() || !villager.playerNear()) return false;
        if (villager.getRandom().nextInt(2) != 0) return false;
        Village village = Villages.get(level).villageAt(villager.blockPosition());
        if (village == null) return false;
        long bell = village.anchorAt(villager.getRandom().nextInt(village.anchorCount()));
        int x = BlockPos.getX(bell) + villager.getRandom().nextInt(7) - 3;
        int z = BlockPos.getZ(bell) + villager.getRandom().nextInt(7) - 3;
        if (!level.hasChunk(x >> 4, z >> 4)) return false;
        point.set(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        if (!Ground.onDryGround(level, point)) return false;
        if (point.distSqr(villager.blockPosition()) > (double) MAX_WALK * MAX_WALK) return false;
        if (!GuardPathBudget.tryTake(level.getGameTime())) return false;
        return villager.getNavigation().moveTo(x + 0.5, point.getY(), z + 0.5, SPEED);
    }

    @Override
    public void start() {
        arrived = false;
        stayUntil = villager.tickCount + STAY + villager.getRandom().nextInt(STAY_JITTER);
        nextTalk = 0;
    }

    @Override
    public boolean canContinueToUse() {
        ServerLevel level = villager.serverLevel();
        return level != null && villager.tickCount < stayUntil && free() && isMeetingTime(Vanilla.timeOfDay(level));
    }

    @Override
    public void stop() {
        partner = null;
        villager.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (!arrived) {
            arrived = villager.getNavigation().isDone();
            return;
        }
        if (villager.tickCount >= nextTalk) {
            nextTalk = villager.tickCount + TALK_INTERVAL;
            partner = findPartner();
        }
        if (partner == null || !partner.isAlive() || villager.distanceToSqr(partner) > TALK_RANGE * TALK_RANGE * 4) return;
        // A look lasts two ticks, so it is renewed every tick the two talk.
        villager.getLookControl().setLookAt(partner, 30.0F, 30.0F);
    }

    /** The nearest villager, wandering trader or villager of the mod within {@link #TALK_RANGE}, or null. */
    private LivingEntity findPartner() {
        ServerLevel level = villager.serverLevel();
        if (level == null) return null;
        List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class,
                villager.getBoundingBox().inflate(TALK_RANGE, 2.0, TALK_RANGE), TALKER);
        LivingEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (int i = 0; i < near.size(); i++) {
            LivingEntity other = near.get(i);
            double distance = villager.distanceToSqr(other);
            if (other == villager || distance >= nearestDistance) continue;
            nearest = other;
            nearestDistance = distance;
        }
        return nearest;
    }
}
