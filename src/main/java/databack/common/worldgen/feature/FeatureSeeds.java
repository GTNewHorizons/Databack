package databack.common.worldgen.feature;

import org.jetbrains.annotations.NotNull;

import databack.common.worldgen.rng.RandomSource;

/// Seeding of the per-chunk decoration randoms (`feature/probes/decoration_seed.tsv`).
public final class FeatureSeeds {

    private FeatureSeeds() {}

    /// Reseeds `random` for the chunk whose lowest block corner is `(minX, minZ)` in a world seeded `worldSeed`, and
    /// returns the new seed: two odd longs drawn after seeding with the world seed scale the coordinates, and the
    /// products are summed and mixed with the world seed.
    public static long decorationSeed(@NotNull RandomSource random, long worldSeed, int minX, int minZ) {
        random.setSeed(worldSeed);
        long xScale = random.nextLong() | 1L, zScale = random.nextLong() | 1L;
        long seed = (long) minX * xScale + (long) minZ * zScale ^ worldSeed;
        random.setSeed(seed);
        return seed;
    }

    /// Reseeds `random` for the `index`th feature of decoration step `step`.
    public static void featureSeed(@NotNull RandomSource random, long decorationSeed, int index, int step) {
        random.setSeed(decorationSeed + index + 10000L * step);
    }
}
