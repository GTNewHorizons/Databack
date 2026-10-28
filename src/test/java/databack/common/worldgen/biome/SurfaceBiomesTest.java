package databack.common.worldgen.biome;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import databack.common.worldgen.biome.zoom.BiomeZoom;
import databack.common.worldgen.biome.zoom.ZoomSource;
import databack.common.worldgen.biome.zoom.ZoomedBiomes;
import databack.common.worldgen.vanilla.VanillaJar;

class SurfaceBiomesTest {

    /// Names each quart cell, so the test sees which one was read.
    private static final ZoomSource<String> CELLS = (qx, qy, qz) -> qx + "," + qy + "," + qz;

    @Test
    void zoomedColumnsMatchDirectLookups() throws IOException {
        BiomeZoom zoom = BiomeZoom.load(VanillaJar.classFile(BiomeZoom.MANAGER_CLASS),
            VanillaJar.classFile(BiomeZoom.LCG_CLASS));
        long zoomSeed = zoom.zoomSeed(42);
        ZoomedBiomes<String> direct = zoom.zoom(CELLS, zoomSeed);

        int[] surface = new int[256];
        for (int i = 0; i < 256; i++) surface[i] = 60 + (i * 7) % 40;
        surface[17] = SurfaceHeights.NONE;

        String[] biomes = new SurfaceBiomes(CELLS, zoom, zoomSeed).columns(-3, 1250, surface, 63);

        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int y = z * 16 + x == 17 ? 63 : surface[z * 16 + x];
                assertEquals(direct.biome(-3 * 16 + x, y, 1250 * 16 + z), biomes[z * 16 + x], "column " + x + ", " + z);
            }
        }
    }

    @Test
    void withoutZoomColumnsReadTheirOwnCell() {
        int[] surface = new int[256];
        Arrays.fill(surface, -1);

        String[] biomes = new SurfaceBiomes(CELLS, null, 0).columns(2, -1, surface, 63);

        assertEquals("8,-1,-4", biomes[0]);
        assertEquals("11,-1,-1", biomes[15 * 16 + 15]);
    }

    @Test
    void eachCellIsComputedOncePerChunk() {
        AtomicInteger calls = new AtomicInteger();
        ZoomSource<String> counting = (qx, qy, qz) -> {
            calls.incrementAndGet();
            return "minecraft:plains";
        };

        new SurfaceBiomes(counting, null, 0).columns(0, 0, new int[256], 63);

        assertEquals(16, calls.get());
    }
}
