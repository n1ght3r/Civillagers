package com.civillagers.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Iterator;
import java.util.function.Predicate;

/**
 * Beds, as vanilla's home points of interest: the one a villager of the mod sleeps in, and the house it
 * hides in. Reads only loaded chunks: {@link PoiManager} would read an unloaded one from disk. Callers
 * rate-limit it.
 */
public final class Homes {
    private static final Predicate<Holder<PoiType>> BED = holder -> holder.is(PoiTypes.HOME);

    private Homes() { }

    /**
     * The nearest bed within {@code radius} of {@code origin}, or null. With {@code free}, only one no
     * villager has claimed and nobody lies in, to sleep in; without, any, to hide beside.
     */
    public static BlockPos nearestBed(ServerLevel level, BlockPos origin, int radius, boolean free) {
        PoiManager poi = level.getPoiManager();
        PoiManager.Occupancy occupancy = free ? PoiManager.Occupancy.HAS_SPACE : PoiManager.Occupancy.ANY;
        BlockPos nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (int x = (origin.getX() - radius) >> 4; x <= (origin.getX() + radius) >> 4; x++) {
            for (int z = (origin.getZ() - radius) >> 4; z <= (origin.getZ() + radius) >> 4; z++) {
                if (!level.hasChunk(x, z)) continue;
                Iterator<PoiRecord> beds = poi.getInChunk(BED, new ChunkPos(x, z), occupancy).iterator();
                while (beds.hasNext()) {
                    BlockPos pos = beds.next().getPos();
                    double distance = pos.distSqr(origin);
                    if (distance >= nearestDistance || distance > (double) radius * radius) continue;
                    BlockState state = level.getBlockState(pos);
                    if (!(state.getBlock() instanceof BedBlock) || free && state.getValue(BedBlock.OCCUPIED)) continue;
                    nearest = pos;
                    nearestDistance = distance;
                }
            }
        }
        return nearest;
    }
}
