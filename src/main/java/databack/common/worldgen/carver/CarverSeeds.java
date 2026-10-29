package databack.common.worldgen.carver;

import org.jetbrains.annotations.NotNull;

import databack.common.worldgen.rng.RandomSource;

/// Seeding of the per-chunk carver randoms (`carver/probes/large_feature_seed.tsv`).
public final class CarverSeeds {

    private CarverSeeds() {}

    /// Reseeds `random` for chunk `(chunkX, chunkZ)` of a world seeded `worldSeed`: two longs drawn after seeding
    /// with the world seed scale the chunk coordinates, and the products are mixed with the world seed.
    public static void largeFeatureSeed(@NotNull RandomSource random, long worldSeed, int chunkX, int chunkZ) {
        random.setSeed(worldSeed);
        long xScale = random.nextLong(), zScale = random.nextLong();
        random.setSeed((long) chunkX * xScale ^ (long) chunkZ * zScale ^ worldSeed);
    }
}
