package databack.common.worldgen.biome;

import java.util.Arrays;
import java.util.Collections;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import databack.common.dto.worldgen.biome.DatapackBiome;
import databack.common.worldgen.noise.PerlinSimplexNoise;
import databack.common.worldgen.rng.StandardRandom;

/// A biome's temperature at a block, which decides where snow and ice form (`feature/probes/climate.tsv`,
/// `frozen_modifier.tsv`, `climate_noise_match.tsv`). Sea level doesn't affect it.
public final class BiomeTemperature {

    /// Below this, it snows instead of raining.
    public static final float COLD = 0.15F;

    /// Above this height, it cools by [#COOLING] a block, shifted per column by [#COOLING_NOISE].
    private static final int COOLING_START = 80;
    private static final float COOLING = 0.00125F;
    private static final PerlinSimplexNoise COOLING_NOISE = noise(1234, 0);

    /// The frozen modifier's warm patches, from a coarse noise and two reads of the biome info noise.
    private static final PerlinSimplexNoise FROZEN_NOISE = noise(3456, -2, -1, 0);
    private static final PerlinSimplexNoise INFO_NOISE = noise(2345, 0);
    private static final float FROZEN_WARM_PATCH = 0.2F;

    private final float base;
    private final boolean frozen;

    public BiomeTemperature(float base, boolean frozen) {
        this.base = base;
        this.frozen = frozen;
    }

    @NotNull
    @Contract("_ -> new")
    public static BiomeTemperature of(@NotNull DatapackBiome biome) {
        return new BiomeTemperature(biome.temperature,
            biome.temperature_modifier == DatapackBiome.TemperatureModifier.frozen);
    }

    private static PerlinSimplexNoise noise(long seed, Integer... octaves) {
        return new PerlinSimplexNoise(new StandardRandom(seed), Collections.unmodifiableList(Arrays.asList(octaves)));
    }

    public float base() {
        return base;
    }

    public float at(int x, int y, int z) {
        float temperature = frozen ? frozenAt(x, z) : base;

        if (y > COOLING_START) {
            float shift = (float) (COOLING_NOISE.getValue(x / 8.0, z / 8.0) * 8.0);
            temperature -= (shift + y - COOLING_START) * COOLING;
        }
        return temperature;
    }

    private float frozenAt(int x, int z) {
        double patch = FROZEN_NOISE.getValue(x * 0.05, z * 0.05) * 7.0 + INFO_NOISE.getValue(x * 0.2, z * 0.2);
        if (patch < 0.3 && INFO_NOISE.getValue(x * 0.09, z * 0.09) < 0.8) return FROZEN_WARM_PATCH;
        return base;
    }

    public boolean coldEnoughToSnow(int x, int y, int z) {
        return at(x, y, z) < COLD;
    }
}
