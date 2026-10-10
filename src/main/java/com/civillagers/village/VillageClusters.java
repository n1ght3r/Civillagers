package com.civillagers.village;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Groups bells into villages: two bells closer than the link distance are in one group, and so is
 * anything linked to either. Pure computation on packed positions, no world access.
 */
public final class VillageClusters {
    private VillageClusters() { }

    /**
     * The connected groups of {@code positions} (packed with {@link BlockPos#asLong()}), each in the
     * order the positions came in, the groups ordered by their first position.
     */
    public static List<LongArrayList> group(LongArrayList positions, int linkDistance) {
        int n = positions.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;
        long limit = (long) linkDistance * linkDistance;
        for (int i = 0; i < n; i++) {
            long a = positions.getLong(i);
            for (int j = i + 1; j < n; j++) {
                if (distanceSq(a, positions.getLong(j)) <= limit) union(parent, i, j);
            }
        }
        List<LongArrayList> groups = new ArrayList<>();
        int[] groupOf = new int[n];
        java.util.Arrays.fill(groupOf, -1);
        for (int i = 0; i < n; i++) {
            int root = find(parent, i);
            if (groupOf[root] < 0) {
                groupOf[root] = groups.size();
                groups.add(new LongArrayList());
            }
            groups.get(groupOf[root]).add(positions.getLong(i));
        }
        return groups;
    }

    static long distanceSq(long a, long b) {
        long dx = BlockPos.getX(a) - BlockPos.getX(b);
        long dy = BlockPos.getY(a) - BlockPos.getY(b);
        long dz = BlockPos.getZ(a) - BlockPos.getZ(b);
        return dx * dx + dy * dy + dz * dz;
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a);
        int rb = find(parent, b);
        if (ra != rb) parent[Math.max(ra, rb)] = Math.min(ra, rb);
    }
}
