package com.civillagers.ai.guard;

import com.civillagers.entity.guard.Guard;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/**
 * Keeps a guard still and facing the player it trades with, as a villager stands for a trade. A guard
 * drops a trade to fight ({@link Guard#aiStep}), so this never keeps it from a target.
 */
public final class GuardTradeGoal extends Goal {
    private final Guard guard;

    public GuardTradeGoal(Guard guard) {
        this.guard = guard;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        return guard.getTradingPlayer() != null;
    }

    @Override
    public void start() {
        guard.getNavigation().stop();
    }

    @Override
    public void tick() {
        Player player = guard.getTradingPlayer();
        if (player != null) guard.getLookControl().setLookAt(player, 30.0F, 30.0F);
    }
}
