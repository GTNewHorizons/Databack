package databack.common.worldgen.density;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import databack.common.dto.worldgen.density_function.IDensityFunctionFactory;
import databack.common.dto.worldgen.noise.DatapackNoise;
import databack.common.dto.worldgen.noise_settings.NoiseGeneratorSettings;
import databack.common.handlers.DatapackNoiseList;
import databack.common.handlers.DensityFunctionList;
import databack.common.worldgen.noise.NormalNoise;
import databack.common.worldgen.rng.RandomFactory;
import databack.common.worldgen.rng.RandomSource;
import databack.common.worldgen.rng.StandardRandom;
import databack.common.worldgen.rng.XoroshiroRandom;

/// Seeds and builds runtime [DensityNode]s from their DTOs: noises are created once per id with 26.1's seeding
/// scheme, and identical DTO subtrees build to the same runtime object.
///
/// Not thread safe. Build everything up front; the built functions themselves are immutable and thread safe.
public final class DensityBuildContext {

    public static final Logger LOGGER = LogManager.getLogger("Databack/DensityBuild");

    private final long seed;
    private final boolean legacyRandomSource;
    private final RandomFactory random;
    private final Function<String, DatapackNoise> noiseLookup;
    private final Function<String, IDensityFunctionFactory> functionLookup;

    private final Map<IDensityFunctionFactory, DensityNode> built = new HashMap<>();
    private final Map<String, SeededNoise> noises = new HashMap<>();
    private RandomSource terrainRandom;
    private DensityNode endIslands;

    public DensityBuildContext(
        long seed, boolean legacyRandomSource, Function<String, DatapackNoise> noiseLookup,
        Function<String, IDensityFunctionFactory> functionLookup
    ) {
        this.seed = seed;
        this.legacyRandomSource = legacyRandomSource;
        this.random = legacyRandomSource ? new StandardRandom(seed).forkFactory() : new XoroshiroRandom(seed).forkFactory();
        this.noiseLookup = noiseLookup;
        this.functionLookup = functionLookup;
    }

    /// Uses the currently loaded datapack registries.
    public static DensityBuildContext fromDatapacks(long seed, boolean legacyRandomSource) {
        DatapackNoiseList noiseList = DatapackNoiseList.RT.getHandler();
        DensityFunctionList functionList = DensityFunctionList.RT.getHandler();
        return new DensityBuildContext(seed, legacyRandomSource, noiseList::getNoise, functionList::getDensityFunction);
    }

    public long seed() {
        return seed;
    }

    public RandomFactory random() {
        return random;
    }

    @Nullable
    public IDensityFunctionFactory lookupFunction(String id) {
        IDensityFunctionFactory f = functionLookup.apply(id);
        if (f == null && id.indexOf(':') < 0) f = functionLookup.apply("minecraft:" + id);
        return f;
    }

    /// Builds (or returns the already built) runtime function for a DTO.
    public DensityNode build(IDensityFunctionFactory factory) {
        DensityNode existing = built.get(factory);
        if (existing != null) return existing;

        // Not computeIfAbsent: building recurses into this method.
        DensityNode result = factory.instantiate(this);
        built.put(factory, result);
        return result;
    }

    /// Returns the seeded noise for an id.
    public SeededNoise noise(String id) {
        String key = id.indexOf(':') < 0 ? "minecraft:" + id : id;

        SeededNoise holder = noises.get(key);
        if (holder != null) return holder;

        DatapackNoise data = noiseLookup.apply(key);
        if (data == null) {
            throw new IllegalArgumentException("Unknown noise: " + key);
        }

        NormalNoise.NoiseParameters params = new NormalNoise.NoiseParameters(data.firstOctave, data.amplitudes);
        NormalNoise noise;

        // These two always use the legacy seeding, regardless of the random source setting.
        if (key.equals("minecraft:nether/temperature")) {
            noise = NormalNoise.createLegacyNetherBiome(new StandardRandom(seed), params);
        } else if (key.equals("minecraft:nether/vegetation")) {
            noise = NormalNoise.createLegacyNetherBiome(new StandardRandom(seed + 1L), params);
        } else {
            noise = NormalNoise.create(random.fromHashOf(key), params);
        }

        holder = new SeededNoise(key, noise);
        noises.put(key, holder);
        return holder;
    }

    /// The random used for `old_blended_noise`. Every blended noise gets a fresh random of the same seed, so this
    /// returns a new instance each call.
    public RandomSource terrainRandom() {
        return legacyRandomSource ? new StandardRandom(seed) : random.fromHashOf("minecraft:terrain");
    }

    public DensityNode endIslands() {
        if (endIslands == null) endIslands = new EndIslands(seed);
        return endIslands;
    }

    /// Builds every field of a noise router. Pass `optimize` to run [DensityOptimizer] over the result.
    public DensityRouter buildRouter(NoiseGeneratorSettings.NoiseRouter router, boolean optimize) {
        Function<IDensityFunctionFactory, DensityNode> b = f -> {
            if (f == null) return Density.ZERO;
            DensityNode df = build(f);
            return optimize ? DensityOptimizer.optimize(df) : df;
        };

        return new DensityRouter(
            b.apply(router.barrier),
            b.apply(router.fluid_level_floodedness),
            b.apply(router.fluid_level_spread),
            b.apply(router.lava),
            b.apply(router.temperature),
            b.apply(router.vegetation),
            b.apply(router.continents),
            b.apply(router.erosion),
            b.apply(router.depth),
            b.apply(router.ridges),
            b.apply(router.preliminary_surface_level),
            b.apply(router.final_density),
            b.apply(router.vein_toggle),
            b.apply(router.vein_ridged),
            b.apply(router.vein_gap));
    }
}
