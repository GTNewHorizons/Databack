package databack.common.worldgen.feature.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.init.Blocks;

import org.junit.jupiter.api.Test;

import databack.common.interop.heightmap.HeightmapType;

/// [HeightCache] scans each column once, again only after a write that could move its surface.
class HeightCacheTest {

    private final FakeLegacyWorld world = new FakeLegacyWorld(Blocks.stone, 64, Blocks.air);
    private final HeightCache heights = new HeightCache(world, ColumnArea.ofChunks(0, 0, 2, 2));

    @Test
    void columnsAreScannedOnce() {
        for (int i = 0; i < 5; i++) assertEquals(64, heights.surface(HeightmapType.OCEAN_FLOOR_WG, 3, 4));
        assertEquals(1, world.surfaceQueries);

        heights.surface(HeightmapType.WORLD_SURFACE_WG, 3, 4);
        heights.surface(HeightmapType.OCEAN_FLOOR_WG, 5, 4);
        assertEquals(3, world.surfaceQueries, "each type and column on its own");
    }

    /// Ores write underground: below the top block, so the surface stays.
    @Test
    void writesBelowTheTopKeepTheColumn() {
        heights.surface(HeightmapType.OCEAN_FLOOR_WG, 3, 4);
        heights.written(3, 62, 4);
        heights.written(4, 70, 4);
        heights.surface(HeightmapType.OCEAN_FLOOR_WG, 3, 4);
        assertEquals(1, world.surfaceQueries);
    }

    /// The top block (63) or anything above it may move the surface.
    @Test
    void writesAtOrAboveTheTopRescanTheColumn() {
        heights.surface(HeightmapType.OCEAN_FLOOR_WG, 3, 4);
        heights.written(3, 63, 4);
        heights.surface(HeightmapType.OCEAN_FLOOR_WG, 3, 4);
        assertEquals(2, world.surfaceQueries);

        heights.written(3, 90, 4);
        heights.surface(HeightmapType.OCEAN_FLOOR_WG, 3, 4);
        assertEquals(3, world.surfaceQueries);
    }

    @Test
    void columnsOutsideTheAreaAreAlwaysAsked() {
        heights.surface(HeightmapType.OCEAN_FLOOR_WG, -1, 4);
        heights.surface(HeightmapType.OCEAN_FLOOR_WG, -1, 4);
        assertEquals(2, world.surfaceQueries);
    }
}
