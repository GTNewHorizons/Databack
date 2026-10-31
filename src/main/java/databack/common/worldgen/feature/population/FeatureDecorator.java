package databack.common.worldgen.feature.population;

import java.util.HashSet;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import databack.common.worldgen.feature.BiomeFeatures;
import databack.common.worldgen.feature.FeatureBand;
import databack.common.worldgen.feature.FeatureSeeds;
import databack.common.worldgen.feature.VanillaFeatures;
import databack.common.worldgen.feature.standin.FtHolder;
import databack.common.worldgen.feature.world.ColumnArea;
import databack.common.worldgen.feature.world.LegacyWorld;
import databack.common.worldgen.feature.world.PopulationLevel;
import databack.common.worldgen.rng.RandomSource;
import databack.common.worldgen.rng.SourceBits;
import databack.common.worldgen.rng.XoroshiroRandom;
import databack.common.worldgen.vanilla.standin.VnBlockPos;

/// Places a chunk's features once [DeferredPopulation] finds its 3×3 generated, as modern decorates: step by step,
/// each feature of a biome found in the chunk, from the chunk's lowest corner, with its own seed. Features that throw
/// are logged once each and skipped.
public final class FeatureDecorator implements DeferredPopulation.Decorator {

    private static final Logger LOGGER = LogManager.getLogger("databack|features");

    /// Loads a chunk known to be generated.
    @FunctionalInterface
    public interface ChunkLoader {

        void load(int chunkX, int chunkZ);
    }

    /// The level features see over a world, limited to an area.
    @FunctionalInterface
    public interface Levels {

        @NotNull
        PopulationLevel create(@NotNull LegacyWorld world, @NotNull ColumnArea area);
    }

    /// The biomes a chunk stores, which pick the features it gets.
    @FunctionalInterface
    public interface ChunkBiomes {

        void collect(int chunkX, int chunkZ, @NotNull Set<String> into);
    }

    @NotNull
    private final ChunkLoader chunks;
    @NotNull
    private final Levels levels;
    @NotNull
    private final LegacyWorld world;
    @NotNull
    private final ChunkBiomes biomes;
    @NotNull
    private final VanillaFeatures vanilla;
    @NotNull
    private final BiomeFeatures features;
    private final long seed;
    private final Set<String> failed = new HashSet<>();

    /// [#decorate] places features into `world`; `seed` is the world's.
    public FeatureDecorator(
        @NotNull ChunkLoader chunks, @NotNull Levels levels, @NotNull LegacyWorld world, @NotNull ChunkBiomes biomes,
        @NotNull VanillaFeatures vanilla, @NotNull BiomeFeatures features, long seed
    ) {
        this.chunks = chunks;
        this.levels = levels;
        this.world = world;
        this.biomes = biomes;
        this.vanilla = vanilla;
        this.features = features;
        this.seed = seed;
    }

    @Override
    public void decorate(int chunkX, int chunkZ) {
        decorate(chunkX, chunkZ, world);
    }

    /// Places the chunk's features positioned in layers `minY..maxY` ([FeatureBand]) into `world`, as [#decorate]
    /// places them all. Features may write outside the band.
    public void decorateBand(int chunkX, int chunkZ, int minY, int maxY, @NotNull LegacyWorld world) {
        FeatureBand.within(minY, maxY, () -> decorate(chunkX, chunkZ, world));
    }

    private void decorate(int chunkX, int chunkZ, LegacyWorld world) {
        for (int x = chunkX - 1; x <= chunkX + 1; x++) {
            for (int z = chunkZ - 1; z <= chunkZ + 1; z++) chunks.load(x, z);
        }

        PopulationLevel level = levels.create(world,
            ColumnArea.ofChunks(chunkX - 1, chunkZ - 1, chunkX + 1, chunkZ + 1));
        Set<String> biomes = new HashSet<>();
        this.biomes.collect(chunkX, chunkZ, biomes);
        VnBlockPos origin = new VnBlockPos(chunkX << 4, level.getMinY(), chunkZ << 4);

        RandomSource random = new SourceBits(new XoroshiroRandom(0));
        long decoration = FeatureSeeds.decorationSeed(random, seed, origin.getX(), origin.getZ());

        for (int step = 0; step < features.steps(); step++) {
            int index = 0;
            for (FtHolder feature : features.order(step)) {
                if (isInAny(biomes, step, feature)) {
                    FeatureSeeds.featureSeed(random, decoration, index, step);
                    place(feature, level, random, origin);
                }
                index++;
            }
        }
    }

    private boolean isInAny(Set<String> biomes, int step, FtHolder feature) {
        for (String biome : biomes) {
            if (features.step(biome, step).contains(feature)) return true;
        }
        return false;
    }

    private void place(FtHolder feature, PopulationLevel level, RandomSource random, VnBlockPos origin) {
        try {
            vanilla.placeWithBiomeCheck(feature.value(), level, features, random, origin);
        } catch (RuntimeException e) {
            String name = String.valueOf(feature.id);
            if (failed.add(name)) LOGGER.warn("The placed feature {} failed and is skipped where it fails", name, e);
        }
    }
}
