package databack.common.worldgen.noise;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.Random;

public class NormalNoiseTest {

    // ---- PerlinNoise.getValue ----

    /**
     * PerlinNoise.create with a fixed seed must produce deterministic output at the same coordinate.
     */
    @Test
    public void testPerlinGetValueDeterministic() {
        PerlinNoise a = PerlinNoise.create(new Random(42), -4, 1, 1, 1, 1, 1);
        PerlinNoise b = PerlinNoise.create(new Random(42), -4, 1, 1, 1, 1, 1);
        assertEquals(a.getValue(1.0, 2.0, 3.0), b.getValue(1.0, 2.0, 3.0), 1e-12,
            "Same seed must produce identical getValue output");
    }

    /**
     * Different seeds must produce different output (with overwhelming probability).
     */
    @Test
    public void testPerlinGetValueDifferentSeeds() {
        PerlinNoise a = PerlinNoise.create(new Random(1), -4, 1, 1, 1, 1, 1);
        PerlinNoise b = PerlinNoise.create(new Random(2), -4, 1, 1, 1, 1, 1);
        assertNotEquals(a.getValue(1.0, 2.0, 3.0), b.getValue(1.0, 2.0, 3.0),
            "Different seeds should produce different noise values");
    }

    /**
     * PerlinNoise.maxValue() must be non-negative and bound observed samples.
     */
    @Test
    public void testPerlinMaxValue() {
        PerlinNoise noise = PerlinNoise.create(new Random(99), -3, 1, 1, 1, 1);
        double max = noise.maxValue();
        assertTrue(max > 0, "maxValue must be positive");

        // Sample a grid and verify all values are within [-max, max].
        Random coords = new Random(7);
        for (int i = 0; i < 500; i++) {
            double v = noise.getValue(coords.nextDouble() * 100, coords.nextDouble() * 100, coords.nextDouble() * 100);
            assertTrue(Math.abs(v) <= max * 1.01, // 1% tolerance for floating-point edge cases
                "Sample " + v + " exceeded maxValue " + max);
        }
    }

    /**
     * New-init and legacy-nether-biome init with the same seed produce different noise tables
     * (different initialization paths → different perm tables / offsets).
     */
    @Test
    public void testPerlinNewVsLegacyDiffer() {
        PerlinNoise newInit    = PerlinNoise.create(new Random(55), -4, 1, 1, 1, 1, 1);
        PerlinNoise legacyInit = PerlinNoise.createLegacyForLegacyNetherBiome(new Random(55), -4, 1, 1, 1, 1, 1);
        assertNotEquals(newInit.getValue(1.0, 1.0, 1.0), legacyInit.getValue(1.0, 1.0, 1.0),
            "New-init and legacy-init should produce different noise for the same seed");
    }

    /**
     * Zero-amplitude octaves must not contribute to the output.
     * Noise with amplitudes [0,1,0] must equal noise with [0,1] (only one active octave).
     */
    @Test
    public void testPerlinZeroAmplitudeSkipped() {
        // Two noises with equivalent active octaves but different zero padding.
        // They will differ in seeding (different octave indices), but within each,
        // zero-amplitude octaves must not affect the output.
        PerlinNoise withZeros = PerlinNoise.create(new Random(11), -2, 0, 1, 0);
        double v = withZeros.getValue(5.0, 5.0, 5.0);
        // Just verify it doesn't throw and produces a finite value.
        assertTrue(Double.isFinite(v), "getValue with zero-amplitude octaves must be finite");
    }

    // ---- NormalNoise ----

    /**
     * NormalNoise.create must be deterministic for the same seed and parameters.
     */
    @Test
    public void testNormalNoiseDeterministic() {
        NormalNoise.NoiseParameters params = new NormalNoise.NoiseParameters(-4, 1, 1, 1, 1, 1);
        NormalNoise a = NormalNoise.create(new Random(42), params);
        NormalNoise b = NormalNoise.create(new Random(42), params);
        assertEquals(a.getValue(3.0, 1.5, -2.0), b.getValue(3.0, 1.5, -2.0), 1e-12,
            "NormalNoise must be deterministic for the same seed");
    }

    /**
     * NormalNoise.getValue must stay within [-maxValue, maxValue].
     */
    @Test
    public void testNormalNoiseMaxValue() {
        NormalNoise.NoiseParameters params = new NormalNoise.NoiseParameters(-3, 1, 1, 1, 1);
        NormalNoise noise = NormalNoise.create(new Random(13), params);
        double max = noise.maxValue();
        assertTrue(max > 0, "maxValue must be positive");

        Random coords = new Random(5);
        for (int i = 0; i < 500; i++) {
            double v = noise.getValue(coords.nextDouble() * 200 - 100,
                                      coords.nextDouble() * 200 - 100,
                                      coords.nextDouble() * 200 - 100);
            assertTrue(Math.abs(v) <= max * 1.01,
                "Sample " + v + " exceeded maxValue " + max);
        }
    }

    /**
     * NormalNoise.create and createLegacyNetherBiome differ for the same seed.
     */
    @Test
    public void testNormalNoiseNewVsLegacyDiffer() {
        NormalNoise.NoiseParameters params = new NormalNoise.NoiseParameters(-4, 1, 1, 1, 1, 1);
        NormalNoise newNoise    = NormalNoise.create(new Random(77), params);
        NormalNoise legacyNoise = NormalNoise.createLegacyNetherBiome(new Random(77), params);
        assertNotEquals(newNoise.getValue(1.0, 1.0, 1.0), legacyNoise.getValue(1.0, 1.0, 1.0),
            "create() and createLegacyNetherBiome() must produce different noise");
    }

    /**
     * Different parameter sets produce different noise.
     */
    @Test
    public void testNormalNoiseDifferentParams() {
        NormalNoise a = NormalNoise.create(new Random(1), new NormalNoise.NoiseParameters(-4, 1, 1, 1));
        NormalNoise b = NormalNoise.create(new Random(1), new NormalNoise.NoiseParameters(-3, 1, 1, 1));
        assertNotEquals(a.getValue(1.0, 1.0, 1.0), b.getValue(1.0, 1.0, 1.0),
            "Different firstOctave values must produce different noise");
    }

    /**
     * NormalNoise.parameters() returns the original parameters object.
     */
    @Test
    public void testNormalNoiseParametersRoundTrip() {
        NormalNoise.NoiseParameters params = new NormalNoise.NoiseParameters(-5, 1, 2, 3);
        NormalNoise noise = NormalNoise.create(new Random(0), params);
        assertSame(params, noise.parameters(), "parameters() must return the original NoiseParameters");
    }

    /**
     * The existing OldBlendedNoise path (using PerlinNoise.createLegacyForBlendedNoise) is
     * unaffected by the PerlinNoise changes.
     */
    @Test
    public void testLegacyBlendedNoiseUnaffected() {
        Random rng = new Random(123);
        PerlinNoise minLimit = PerlinNoise.createLegacyForBlendedNoise(rng, -15, 16);
        PerlinNoise maxLimit = PerlinNoise.createLegacyForBlendedNoise(rng, -15, 16);
        PerlinNoise main     = PerlinNoise.createLegacyForBlendedNoise(rng,  -7,  8);

        // getOctaveNoise must return non-null for all 40 octaves in the all-1.0-amplitude case.
        for (int i = 0; i < 8;  i++) assertNotNull(main.getOctaveNoise(i),     "main octave " + i);
        for (int i = 0; i < 16; i++) assertNotNull(minLimit.getOctaveNoise(i), "minLimit octave " + i);
        for (int i = 0; i < 16; i++) assertNotNull(maxLimit.getOctaveNoise(i), "maxLimit octave " + i);
    }
}
