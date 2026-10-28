package databack.common.worldgen.biome;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SurfaceHeightsTest {

    @Test
    void topSolidBlockPerColumn() {
        int minY = -16, height = 64;
        double[] density = new double[height * 256];

        // Column (x=3, z=5): solid below y=10 and a floating block at y=40. Column (x=0, z=0): solid only at minY.
        for (int y = minY; y < 10; y++) density[((y - minY) * 16 + 5) * 16 + 3] = 1;
        density[((40 - minY) * 16 + 5) * 16 + 3] = 0.01;
        density[0] = 1;

        int[] heights = SurfaceHeights.fromDensity(density, minY, height);

        assertEquals(40, heights[5 * 16 + 3]);
        assertEquals(minY, heights[0]);
        assertEquals(SurfaceHeights.NONE, heights[1]);
    }
}
