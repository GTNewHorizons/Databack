package databack.common.worldgen.noise;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import databack.common.worldgen.rng.RandomSource;

/**
 * Port of net.minecraft.world.level.levelgen.synth.PerlinNoise.
 * <p>
 * Two initialization modes are supported:
 * <ul>
 *   <li><b>Legacy blended-noise</b> ({@link #createLegacyForBlendedNoise}): consumes RNG
 *       sequentially; used only by {@code OldBlendedNoise}.</li>
 *   <li><b>Legacy nether-biome</b> ({@link #createLegacyForLegacyNetherBiome}): also consumes
 *       RNG sequentially but respects the amplitude array; used by deprecated
 *       {@code NormalNoise.createLegacyNetherBiome}.</li>
 *   <li><b>New (positional)</b> ({@link #create}): each octave is independently seeded via
 *       MD5 of {@code "octave_N"} XOR'd with a fork of the input RNG, approximating modern
 *       MC's {@code PositionalRandomFactory.fromHashOf}.</li>
 * </ul>
 */
public final class PerlinNoise {

    final ImprovedNoise[] noiseLevels;
    final double[] amplitudes;
    /** Geometric-series normalizer: 2^(N-1) / (2^N - 1). Applied per octave, halved each step. */
    final double lowestFreqValueFactor;
    /** Input scale for octave 0 (lowest frequency): 2^firstOctave. 0 for the blended-noise path. */
    final double lowestFreqInputFactor;

    // ---- Public factory methods ----

    /**
     * Creates a legacy PerlinNoise for use in OldBlendedNoise.
     * All amplitudes are 1.0; RNG consumed sequentially.
     */
    public static PerlinNoise createLegacyForBlendedNoise(RandomSource random, int firstOctave, int numOctaves) {
        double[] amplitudes = new double[numOctaves];
        for (int i = 0; i < numOctaves; i++) amplitudes[i] = 1.0;
        return new PerlinNoise(random, firstOctave, amplitudes);
    }

    /**
     * Creates a PerlinNoise using the modern positional initialization for use in
     * {@link NormalNoise}.  Each octave is independently seeded via MD5 of
     * {@code "octave_N"} XOR'd with a fork seed drawn from {@code random}, approximating
     * modern MC's {@code PositionalRandomFactory.fromHashOf}.
     */
    public static PerlinNoise create(RandomSource random, int firstOctave, double... amplitudes) {
        return new PerlinNoise(random, firstOctave, amplitudes, true);
    }

    /**
     * Creates a legacy PerlinNoise for the deprecated legacy-nether-biome {@link NormalNoise}.
     * RNG consumed sequentially, same as the blended-noise path.
     */
    public static PerlinNoise createLegacyForLegacyNetherBiome(RandomSource random, int firstOctave, double... amplitudes) {
        return new PerlinNoise(random, firstOctave, amplitudes, false);
    }

    // ---- Constructors ----

    /**
     * Legacy blended-noise constructor (used only by {@link #createLegacyForBlendedNoise}).
     * Kept separate so OldBlendedNoise's call-sites are unchanged.
     */
    private PerlinNoise(RandomSource random, int firstOctave, double[] amplitudes) {
        int numOctaves = amplitudes.length;
        int zeroOctaveIndex = -firstOctave;

        this.amplitudes = amplitudes;
        this.noiseLevels = new ImprovedNoise[numOctaves];
        this.lowestFreqInputFactor = 0.0; // not used by OldBlendedNoise

        // Legacy (non-positional) init: consume RNG sequentially.
        ImprovedNoise zeroOctave = new ImprovedNoise(random);
        if (zeroOctaveIndex >= 0 && zeroOctaveIndex < numOctaves && amplitudes[zeroOctaveIndex] != 0.0) {
            this.noiseLevels[zeroOctaveIndex] = zeroOctave;
        }
        for (int i = zeroOctaveIndex - 1; i >= 0; i--) {
            if (i < numOctaves) {
                if (amplitudes[i] != 0.0) {
                    this.noiseLevels[i] = new ImprovedNoise(random);
                    continue;
                }
            }
            skipOctave(random);
        }

        this.lowestFreqValueFactor = Math.pow(2.0, numOctaves - 1) / (Math.pow(2.0, numOctaves) - 1.0);
    }

    /**
     * Constructor for the NormalNoise paths.
     *
     * @param newInit {@code true} for positional (new) seeding, {@code false} for legacy-nether sequential seeding
     */
    private PerlinNoise(RandomSource random, int firstOctave, double[] amplitudes, boolean newInit) {
        int numOctaves = amplitudes.length;
        this.amplitudes = amplitudes;
        this.noiseLevels = new ImprovedNoise[numOctaves];
        this.lowestFreqInputFactor = Math.pow(2.0, firstOctave);

        if (newInit) {
            // Each octave gets its own independently seeded RNG.
            var fork = random.forkFactory();

            for (int i = 0; i < numOctaves; i++) {
                if (amplitudes[i] != 0.0) {
                    this.noiseLevels[i] = new ImprovedNoise(fork.fromHashOf("octave_" + (firstOctave + i)));
                }
            }
        } else {
            // Legacy sequential init (createLegacyForLegacyNetherBiome).
            int zeroOctaveIndex = -firstOctave;
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
        }

        this.lowestFreqValueFactor = Math.pow(2.0, numOctaves - 1) / (Math.pow(2.0, numOctaves) - 1.0);
    }

    // ---- NormalNoise-facing methods ----

    /**
     * Samples the multi-octave noise at {@code (x, y, z)}.
     * Octave {@code i} is evaluated at input scale {@code lowestFreqInputFactor * 2^i}
     * with amplitude weight {@code amplitudes[i] * lowestFreqValueFactor * 2^(-i)}.
     */
    public double getValue(double x, double y, double z) {
        double value = 0.0;
        double factor = this.lowestFreqInputFactor;
        double valueFactor = this.lowestFreqValueFactor;
        for (int i = 0; i < this.noiseLevels.length; i++) {
            ImprovedNoise noise = this.noiseLevels[i];
            if (noise != null) {
                value += this.amplitudes[i]
                    * noise.noise(wrap(x * factor), wrap(y * factor), wrap(z * factor), 0.0, 0.0)
                    * valueFactor;
            }
            factor *= 2.0;
            valueFactor /= 2.0;
        }
        return value;
    }

    /** Approximate upper bound on the absolute value returned by {@link #getValue}. */
    public double maxValue() {
        return edgeValue(2.0);
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
        return x - (double) lfloor(x / 3.3554432E7 + 0.5) * 3.3554432E7;
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

    /**
     * Advances the RNG by exactly the number of calls that {@link ImprovedNoise#ImprovedNoise(RandomSource)} makes:
     * 3 nextDouble (xo/yo/zo) + 256 nextInt (Fisher-Yates shuffle).
     */
    private static void skipOctave(RandomSource random) {
        random.nextDouble();
        random.nextDouble();
        random.nextDouble();
        for (int i = 0; i < 256; i++) {
            random.nextInt(256 - i);
        }
    }

    private static long lfloor(double x) {
        long lx = (long) x;
        return x < lx ? lx - 1L : lx;
    }

    /**
     * Hashes an octave key to a {@code long} seed, approximating modern MC's
     * {@code PositionalRandomFactory.fromHashOf} behaviour.
     * Uses MD5 of the UTF-8 bytes; the first 8 bytes are read as a little-endian long.
     */
    private static long hashOctaveKey(String key) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] digest = md5.digest(key.getBytes(StandardCharsets.UTF_8));
            long seed = 0L;
            for (int i = 0; i < 8; i++) {
                seed |= (long) (digest[i] & 0xFF) << (i * 8);
            }
            return seed;
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("MD5 not available", e);
        }
    }
}
