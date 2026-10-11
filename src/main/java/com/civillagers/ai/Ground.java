package com.civillagers.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Where a villager of the mod may be sent to stand, for every goal that picks a point to walk to. */
public final class Ground {
    private Ground() { }

    /**
     * Whether {@code top}, the first free block above the ground as the heightmap gives it, stands on dry
     * ground. The heightmap counts water as ground, so without this a villager would walk out onto a pond
     * or a river and stand in it.
     */
    public static boolean onDryGround(ServerLevel level, BlockPos top) {
        BlockPos below = top.below();
        return level.getFluidState(below).isEmpty() && level.getFluidState(top).isEmpty();
    }
}
