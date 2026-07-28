package databack.common.worldgen.noise;

import java.util.Random;

/**
 * Port of net.minecraft.world.level.levelgen.synth.NormalNoise.
 * <p>
 * Combines two {@link PerlinNoise} stacks sampled at slightly offset input scales
 * ({@code INPUT_FACTOR ≈ 1.0181}) to produce a smoother, more isotropic noise field.
 * The combined output is scaled by {@code valueFactor} so that the expected standard
 * deviation matches {@code TARGET_DEVIATION = 1/3}.
 * <p>
 * <b>Seeding note:</b> {@link #create} uses the modern positional initialization
 * (approximated via MD5-hashed octave keys); {@link #createLegacyNetherBiome} uses the
 * old sequential RNG consumption.  Neither path will produce bit-identical values to
 * vanilla 1.21, which uses Xoroshiro128++ internally.
 */
public final class NormalNoise {

    /** Scale applied to the second PerlinNoise's input coordinates. */
    private static final double INPUT_FACTOR = 1.0181268882175227;

    final double valueFactor;
    final PerlinNoise first;
    final PerlinNoise second;
    private final double maxValue;
    private final NoiseParameters parameters;

    // ---- Public factory methods ----

    /**
     * Creates a NormalNoise using modern positional initialization.
     * Matches {@code NormalNoise.create(RandomSource, NoiseParameters)} in vanilla 1.21.
     */
    public static NormalNoise create(Random random, NoiseParameters parameters) {
        return new NormalNoise(random, parameters, true);
    }

    /**
     * Creates a NormalNoise using the deprecated legacy-nether-biome sequential initialization.
     * Matches {@code NormalNoise.createLegacyNetherBiome(RandomSource, NoiseParameters)}.
     */
    public static NormalNoise createLegacyNetherBiome(Random random, NoiseParameters parameters) {
        return new NormalNoise(random, parameters, false);
    }

    // ---- Constructor ----

    private NormalNoise(Random random, NoiseParameters parameters, boolean useNewInit) {
        this.parameters = parameters;
        int firstOctave = parameters.firstOctave;
        double[] amplitudes = parameters.amplitudes;

        if (useNewInit) {
            this.first  = PerlinNoise.create(random, firstOctave, amplitudes);
            this.second = PerlinNoise.create(random, firstOctave, amplitudes);
        } else {
            this.first  = PerlinNoise.createLegacyForLegacyNetherBiome(random, firstOctave, amplitudes);
            this.second = PerlinNoise.createLegacyForLegacyNetherBiome(random, firstOctave, amplitudes);
        }

        // Determine the span of non-zero amplitudes to compute the expected deviation.
        int minOctave = Integer.MAX_VALUE, maxOctave = Integer.MIN_VALUE;
        for (int i = 0; i < amplitudes.length; i++) {
            if (amplitudes[i] != 0.0) {
                minOctave = Math.min(minOctave, i);
                maxOctave = Math.max(maxOctave, i);
            }
        }
        // valueFactor normalises so the expected output std-dev ≈ TARGET_DEVIATION (1/3).
        this.valueFactor = 0.16666666666666666 / expectedDeviation(maxOctave - minOctave);
        this.maxValue = (this.first.maxValue() + this.second.maxValue()) * this.valueFactor;
    }

    // ---- Public API ----

    /** Evaluates the noise at {@code (x, y, z)}. */
    public double getValue(double x, double y, double z) {
        double x2 = x * INPUT_FACTOR, y2 = y * INPUT_FACTOR, z2 = z * INPUT_FACTOR;
        return (this.first.getValue(x, y, z) + this.second.getValue(x2, y2, z2)) * this.valueFactor;
    }

    /** Approximate upper bound on the absolute value returned by {@link #getValue}. */
    public double maxValue() {
        return this.maxValue;
    }

    public NoiseParameters parameters() {
        return this.parameters;
    }

    // ---- Helper ----

    private static double expectedDeviation(int octaveSpan) {
        return 0.1 * (1.0 + 1.0 / (double) (octaveSpan + 1));
    }

    // ---- NoiseParameters ----

    /**
     * Holds the configuration for a NormalNoise: the starting octave index and per-octave
     * amplitudes. Corresponds to {@code NormalNoise.NoiseParameters} (a record) in vanilla 1.21.
     */
    public static class NoiseParameters {

        /** Index of the lowest-frequency (first) octave (typically negative). */
        public final int firstOctave;

        /**
         * Per-octave amplitude multipliers, ordered from lowest to highest frequency.
         * A value of 0.0 skips that octave entirely.
         */
        public final double[] amplitudes;

        public NoiseParameters(int firstOctave, double... amplitudes) {
            this.firstOctave = firstOctave;
            this.amplitudes = amplitudes;
        }
    }
}
