package com.civillagers.ai;

import com.civillagers.entity.CivVillager;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;

import java.util.EnumSet;
import java.util.List;

/**
 * Once a raid on the village is won ({@link CivVillager#raidWon()}), a villager of the mod celebrates as
 * villagers do until the raid is over: it jumps about, cheers, and now and then sends up a firework of
 * one colour. Nothing here looks at the world beyond the raid lookup the villager already makes, and it
 * asks at most every {@link #CHECK_INTERVAL} ticks.
 */
public final class CivCelebrateGoal extends Goal {
    static final int CHECK_INTERVAL = 20;

    private final CivVillager villager;
    private int nextCheck;

    public CivCelebrateGoal(CivVillager villager) {
        this.villager = villager;
        setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (villager.tickCount < nextCheck) return false;
        nextCheck = villager.tickCount + CHECK_INTERVAL;
        return celebrating();
    }

    @Override
    public boolean canContinueToUse() {
        return celebrating();
    }

    private boolean celebrating() {
        return villager.getTarget() == null && !villager.isSleeping() && villager.raidWon();
    }

    @Override
    public void tick() {
        RandomSource random = villager.getRandom();
        if (villager.onGround() && random.nextInt(15) == 0) villager.getJumpControl().jump();
        if (random.nextInt(60) == 0) villager.playSound(SoundEvents.VILLAGER_CELEBRATE, 1.0F, villager.getVoicePitch());
        ServerLevel level = villager.serverLevel();
        if (level != null && random.nextInt(200) == 0) launchFirework(level, villager, random);
    }

    /** Sends up a small firework of one random colour from above {@code villager}'s head. */
    public static void launchFirework(ServerLevel level, CivVillager villager, RandomSource random) {
        DyeColor colour = DyeColor.values()[random.nextInt(DyeColor.values().length)];
        FireworkExplosion explosion = new FireworkExplosion(FireworkExplosion.Shape.SMALL_BALL,
                IntList.of(colour.getFireworkColor()), IntList.of(), false, false);
        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
        rocket.set(DataComponents.FIREWORKS, new Fireworks(1, List.of(explosion)));
        level.addFreshEntity(new FireworkRocketEntity(level, villager.getX(), villager.getEyeY(), villager.getZ(), rocket));
    }
}
