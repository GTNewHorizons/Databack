package databack.common.worldgen.feature.world;

import java.util.Arrays;

import org.jetbrains.annotations.NotNull;

import databack.common.interop.heightmap.HeightmapType;

/// The heightmaps of an area's columns, each scanned from the world once. A heightmap's surface is the layer above
/// its top block, so writing below the top can't move it; writing at or above it may, and the column is scanned
/// again. Columns outside the area aren't kept. Not thread-safe; one per [PopulationLevel].
final class HeightCache {

    private static final int UNKNOWN = Integer.MIN_VALUE;
    private static final HeightmapType[] TYPES = HeightmapType.values();

    @NotNull
    private final LegacyWorld world;
    @NotNull
    private final ColumnArea area;
    private final int width;
    /// By type, then column (`(z - minZ) * width + x - minX`).
    private final int[][] surfaces = new int[TYPES.length][];

    HeightCache(@NotNull LegacyWorld world, @NotNull ColumnArea area) {
        this.world = world;
        this.area = area;
        this.width = area.maxX - area.minX + 1;
    }

    /// As [LegacyWorld#surface].
    int surface(@NotNull HeightmapType heightmap, int x, int z) {
        if (!area.contains(x, z)) return world.surface(heightmap, x, z);

        int[] columns = columns(heightmap);
        int column = column(x, z);
        if (columns[column] == UNKNOWN) columns[column] = world.surface(heightmap, x, z);
        return columns[column];
    }

    /// Call after writing at `(x, y, z)`.
    void written(int x, int y, int z) {
        if (!area.contains(x, z)) return;

        int column = column(x, z);
        for (int[] columns : surfaces) {
            if (columns != null && columns[column] != UNKNOWN && y >= columns[column] - 1) columns[column] = UNKNOWN;
        }
    }

    private int[] columns(HeightmapType heightmap) {
        int[] columns = surfaces[heightmap.ordinal()];
        if (columns == null) {
            columns = new int[width * (area.maxZ - area.minZ + 1)];
            Arrays.fill(columns, UNKNOWN);
            surfaces[heightmap.ordinal()] = columns;
        }
        return columns;
    }

    private int column(int x, int z) {
        return (z - area.minZ) * width + x - area.minX;
    }
}
