package databack.common.worldgen.feature;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import databack.common.dto.worldgen.biome.DatapackBiome;
import databack.common.dto.worldgen.placed_feature.IPlacedFeatureRef;
import databack.common.worldgen.feature.standin.FtBiomeFeatures;
import databack.common.worldgen.feature.standin.FtChunkGenerator;
import databack.common.worldgen.feature.standin.FtHolder;

/// A dimension's biomes' features, built once: each biome's placed features per decoration step, the order they run
/// in across biomes ([FeatureOrder]), and the generator stand-in vanilla's biome checks ask.
public final class BiomeFeatures implements FtChunkGenerator {

    private final int minY, genDepth;
    /// By biome id, then step.
    private final Map<String, List<List<FtHolder>>> biomes = new HashMap<>();
    /// The vanilla placed features of each biome, any step.
    private final Map<String, Set<Object>> placed = new HashMap<>();
    private final List<List<FtHolder>> order = new ArrayList<>();

    /// `possibleBiomes` are the dimension's biomes; `definitions` gives a biome's definition, or null. The generator
    /// makes layers `minY..minY + genDepth`.
    public BiomeFeatures(
        @NotNull FeatureBuildContext ctx, @NotNull Collection<String> possibleBiomes,
        @NotNull Function<String, DatapackBiome> definitions, int minY, int genDepth
    ) {
        this.minY = minY;
        this.genDepth = genDepth;

        int steps = 0;
        for (String biome : new TreeSet<>(possibleBiomes)) {
            List<List<FtHolder>> built = build(ctx, definitions.apply(biome));
            biomes.put(biome, built);
            steps = Math.max(steps, built.size());

            Set<Object> features = Collections.newSetFromMap(new IdentityHashMap<>());
            for (List<FtHolder> step : built) {
                for (FtHolder feature : step) features.add(feature.value());
            }
            placed.put(biome, features);
        }

        for (int step = 0; step < steps; step++) {
            List<List<FtHolder>> perBiome = new ArrayList<>();
            for (String biome : new TreeSet<>(biomes.keySet())) perBiome.add(step(biome, step));
            order.add(FeatureOrder.of(perBiome));
        }
    }

    private static List<List<FtHolder>> build(FeatureBuildContext ctx, @Nullable DatapackBiome definition) {
        List<List<FtHolder>> steps = new ArrayList<>();
        if (definition == null || definition.features == null) return steps;

        for (IPlacedFeatureRef[] refs : definition.features) {
            List<FtHolder> step = new ArrayList<>(refs.length);
            for (IPlacedFeatureRef ref : refs) {
                FtHolder feature = ref.instantiate(ctx);
                if (feature != null) step.add(feature);
            }
            steps.add(step);
        }
        return steps;
    }

    /// The number of decoration steps.
    public int steps() {
        return order.size();
    }

    /// Every biome's features of decoration step `step`, in the order they run; a feature's index is its seed index.
    @NotNull
    public List<FtHolder> order(int step) {
        return order.get(step);
    }

    /// `biome`'s features of decoration step `step` that could be built, in order; none for unknown biomes.
    @NotNull
    public List<FtHolder> step(@NotNull String biome, int step) {
        List<List<FtHolder>> steps = biomes.get(biome);
        return steps == null || step >= steps.size() ? Collections.emptyList() : steps.get(step);
    }

    @Override
    public int getMinY() {
        return minY;
    }

    @Override
    public int getGenDepth() {
        return genDepth;
    }

    @NotNull
    @Override
    public FtBiomeFeatures getBiomeGenerationSettings(@NotNull FtHolder biome) {
        Set<Object> features = placed.getOrDefault(biome.id, Collections.emptySet());
        return features::contains;
    }
}
