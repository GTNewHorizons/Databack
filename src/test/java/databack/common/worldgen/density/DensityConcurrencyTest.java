package databack.common.worldgen.density;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/// The shared sampler state (column cache, per-thread NoiseChunks, prefetching) must never change results.
class DensityConcurrencyTest {

    static DensityRouter router;
    static VanillaWorldgen.Settings settings;

    @BeforeAll
    static void setup() {
        assumeTrue(VanillaWorldgen.available(), "Vanilla datapack not extracted");
        settings = VanillaWorldgen.settings("overworld");
        router = VanillaWorldgen.router("overworld", 42L, true);
    }

    private ChunkDensitySampler sampler(int cacheSize) {
        return new ChunkDensitySampler(router.finalDensity, settings.cellWidth(), settings.cellHeight(), 0, 256, cacheSize);
    }

    /// A fresh, cache-free sampler per chunk: the reference.
    private double[] reference(int x, int z) {
        ChunkDensitySampler s = sampler(0);
        double[] out = s.newBuffer();
        s.sample(x, z, out);
        return out;
    }

    @Test
    void manyThreadsSharingOneSampler() throws Exception {
        ChunkDensitySampler shared = sampler(64);
        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            List<int[]> chunks = new ArrayList<>();
            for (int z = -3; z <= 3; z++) for (int x = -3; x <= 3; x++) chunks.add(new int[] { x, z });

            List<Future<double[]>> results = new ArrayList<>();
            for (int[] c : chunks) {
                results.add(pool.submit(() -> {
                    double[] out = shared.newBuffer();
                    shared.sample(c[0], c[1], out);
                    return out;
                }));
            }

            for (int i = 0; i < chunks.size(); i++) {
                int[] c = chunks.get(i);
                assertArrayEquals(reference(c[0], c[1]), results.get(i).get(), "chunk " + c[0] + ", " + c[1]);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void prefetchedChunksMatchDirectSampling() {
        DensityPrefetcher prefetcher = new DensityPrefetcher(sampler(256), 1, 32);
        // Walk a line; every chunk after the first should have been prefetched by its predecessor.
        for (int x = 0; x < 6; x++) {
            double[] got = prefetcher.get(x, 5, (cx, cz) -> true);
            assertArrayEquals(reference(x, 5), got, "chunk " + x + ", 5");
        }
    }

    @Test
    void columnCacheIsUsedAndHarmless() {
        ChunkDensitySampler s = sampler(1024);
        double[] out = s.newBuffer();
        for (int x = 0; x < 4; x++) {
            s.sample(x, 0, out);
            assertArrayEquals(reference(x, 0), out);
        }
        // 4 chunks in a row share 3 edges of 5 columns each.
        assertEquals(15, s.columnCache().hits());
    }
}
