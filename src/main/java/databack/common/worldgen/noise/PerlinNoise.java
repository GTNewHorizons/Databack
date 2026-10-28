package databack.common.worldgen.noise;

import databack.common.worldgen.rng.RandomSource;

/**
 * Port of net.minecraft.world.level.levelgen.synth.PerlinNoise.
 * <p>
 * Three initialization modes are supported, matching vanilla:
 * <ul>
 *   <li><b>Legacy blended-noise</b> ({@link #createLegacyForBlendedNoise}): consumes RNG
 *       sequentially; used only by {@code OldBlendedNoise}.</li>
 *   <li><b>Legacy nether-biome</b> ({@link #createLegacyForLegacyNetherBiome}): also consumes
 *       RNG sequentially but respects the amplitude array; used by
 *       {@code NormalNoise.createLegacyNetherBiome}.</li>
 *   <li><b>New (positional)</b> ({@link #create}): each octave is independently seeded from a
 *       positional fork of the input RNG via {@code fromHashOf("octave_N")}.</li>
 * </ul>
 * {@link #getValue} walks a compacted list of the non-null octaves with their scale factors precomputed. The factors
 * are produced by the same repeated doubling/halving vanilla does, so results are bit-identical.
 */
public final class PerlinNoise {

    final ImprovedNoise[] noiseLevels;
    final double[] amplitudes;
    /** Geometric-series normalizer: 2^(N-1) / (2^N - 1). Applied per octave, halved each step. */
    final double lowestFreqValueFactor;
    /** Input scale for octave 0 (lowest frequency): 2^firstOctave. */
    final double lowestFreqInputFactor;
    private final double maxValue;

    // Compacted non-null octaves, in increasing frequency order.
    private final ImprovedNoise[] activeLevels;
    private final double[] activeAmplitudes;
    private final double[] activeInputFactors;
    private final double[] activeValueFactors;

    // ---- Public factory methods ----

    /**
     * Creates a legacy PerlinNoise for use in OldBlendedNoise: octaves {@code firstOctave .. firstOctave+numOctaves-1}
     * all with amplitude 1, RNG consumed sequentially.
     */
    public static PerlinNoise createLegacyForBlendedNoise(RandomSource random, int firstOctave, int numOctaves) {
        double[] amplitudes = new double[numOctaves];
        for (int i = 0; i < numOctaves; i++) amplitudes[i] = 1.0;
        return new PerlinNoise(random, firstOctave, amplitudes, false);
    }

    /** Creates a PerlinNoise using the modern positional initialization (used by {@link NormalNoise}). */
    public static PerlinNoise create(RandomSource random, int firstOctave, double... amplitudes) {
        return new PerlinNoise(random, firstOctave, amplitudes, true);
    }

    /** Creates a legacy PerlinNoise for the deprecated legacy-nether-biome {@link NormalNoise}. */
    public static PerlinNoise createLegacyForLegacyNetherBiome(RandomSource random, int firstOctave, double... amplitudes) {
        return new PerlinNoise(random, firstOctave, amplitudes, false);
    }

    // ---- Constructor ----

    private PerlinNoise(RandomSource random, int firstOctave, double[] amplitudes, boolean newInit) {
        int numOctaves = amplitudes.length;
        int zeroOctaveIndex = -firstOctave;
        this.amplitudes = amplitudes;
        this.noiseLevels = new ImprovedNoise[numOctaves];

        if (newInit) {
            var fork = random.forkFactory();

            for (int i = 0; i < numOctaves; i++) {
                if (amplitudes[i] != 0.0) {
                    this.noiseLevels[i] = new ImprovedNoise(fork.fromHashOf("octave_" + (firstOctave + i)));
                }
            }
        } else {
            ImprovedNoise zeroOctave = new ImprovedNoise(random);
            if (zeroOctaveIndex >= 0 && zeroOctaveIndex < numOctaves && amplitudes[zeroOctaveIndex] != 0.0) {
                this.noiseLevels[zeroOctaveIndex] = zeroOctave;
            }
            for (int i = zeroOctaveIndex - 1; i >= 0; i--) {
                if (i < numOctaves && amplitudes[i] != 0.0) {
                    this.noiseLevels[i] = new ImprovedNoise(random);
                } else {
                    skipOctave(random);
                }
            }
            if (zeroOctaveIndex < numOctaves - 1) {
                throw new IllegalArgumentException("Positive octaves are temporarily disabled");
            }
        }

        this.lowestFreqInputFactor = Math.pow(2.0, -zeroOctaveIndex);
        this.lowestFreqValueFactor = Math.pow(2.0, numOctaves - 1) / (Math.pow(2.0, numOctaves) - 1.0);
        this.maxValue = edgeValue(2.0);

        int active = 0;
        for (ImprovedNoise level : noiseLevels) if (level != null) active++;
        this.activeLevels = new ImprovedNoise[active];
        this.activeAmplitudes = new double[active];
        this.activeInputFactors = new double[active];
        this.activeValueFactors = new double[active];

        double factor = this.lowestFreqInputFactor;
        double valueFactor = this.lowestFreqValueFactor;
        for (int i = 0, k = 0; i < numOctaves; i++) {
            if (noiseLevels[i] != null) {
                activeLevels[k] = noiseLevels[i];
                activeAmplitudes[k] = amplitudes[i];
                activeInputFactors[k] = factor;
                activeValueFactors[k] = valueFactor;
                k++;
            }
            factor *= 2.0;
            valueFactor /= 2.0;
        }
    }

    // ---- NormalNoise-facing methods ----

    /**
     * Samples the multi-octave noise at {@code (x, y, z)}.
     * Octave {@code i} is evaluated at input scale {@code lowestFreqInputFactor * 2^i}
     * with amplitude weight {@code amplitudes[i] * lowestFreqValueFactor * 2^(-i)}.
     */
    public double getValue(double x, double y, double z) {
        final ImprovedNoise[] levels = this.activeLevels;
        final double[] amps = this.activeAmplitudes, inputs = this.activeInputFactors, values = this.activeValueFactors;
        double value = 0.0;
        for (int k = 0; k < levels.length; k++) {
            double factor = inputs[k];
            double noiseVal = levels[k].noise(wrap(x * factor), wrap(y * factor), wrap(z * factor));
            value += amps[k] * noiseVal * values[k];
        }
        return value;
    }

    /** Upper bound on the absolute value returned by {@link #getValue}. */
    public double maxValue() {
        return maxValue;
    }

    // ---- OldBlendedNoise-facing methods ----

    /** Returns the ImprovedNoise for loop index {@code i} (0 = highest-frequency octave). */
    public ImprovedNoise getOctaveNoise(int i) {
        return this.noiseLevels[this.noiseLevels.length - 1 - i];
    }

    /** Upper-bound estimate used to compute {@code maxValue} of OldBlendedNoise. */
    public double maxBrokenValue(double yScale) {
        return edgeValue(yScale + 2.0);
    }

    // ---- Shared helpers ----

    /** Reduces a coordinate to the [-33554432, 33554432] range used by ImprovedNoise sampling. */
    public static double wrap(double x) {
        // Below 2^24 the rounded multiple is always 0, and x - 0.0 == x, so skip the division and floor.
        if (x > -1.6777216E7 && x < 1.6777216E7) return x;
        return x - (double) (long) Math.floor(x / 3.3554432E7 + 0.5) * 3.3554432E7;
    }

    private double edgeValue(double noiseValue) {
        double value = 0.0;
        double valueFactor = this.lowestFreqValueFactor;
        for (int i = 0; i < this.noiseLevels.length; i++) {
            if (this.noiseLevels[i] != null) {
                value += this.amplitudes[i] * noiseValue * valueFactor;
            }
            valueFactor /= 2.0;
        }
        return value;
    }

    /** Vanilla skips an octave with {@code random.consumeCount(262)}. */
    private static void skipOctave(RandomSource random) {
        random.consumeCount(262);
    }
}
