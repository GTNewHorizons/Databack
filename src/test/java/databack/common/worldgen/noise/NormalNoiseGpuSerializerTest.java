package databack.common.worldgen.noise;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.Random;

public class NormalNoiseGpuSerializerTest {

    private static NormalNoise makeNoise(long seed, int firstOctave, double... amplitudes) {
        return NormalNoise.create(new Random(seed),
            new NormalNoise.NoiseParameters(firstOctave, amplitudes));
    }

    /**
     * Header fields (valueFactor, numOctaves, lowestFreqInputFactor, lowestFreqValueFactor)
     * must round-trip through float correctly.
     */
    @Test
    public void testHeaderFields() {
        NormalNoise noise = makeNoise(42, -4, 1, 1, 1, 1, 1);
        int[] data = NormalNoiseGpuSerializer.toGpuData(noise);

        int N = noise.first.noiseLevels.length;
        assertEquals(N, 5, "5 amplitudes → 5 octaves");
        assertEquals(N, Integer.toUnsignedLong(data[1]), "data[1] = numOctaves");

        float valueFactor = Float.intBitsToFloat(data[0]);
        assertEquals((float) noise.valueFactor, valueFactor, 0.0f, "valueFactor round-trips");

        float lowestInF = Float.intBitsToFloat(data[2]);
        assertEquals((float) noise.first.lowestFreqInputFactor, lowestInF, 0.0f,
            "lowestFreqInputFactor round-trips");

        float lowestVaF = Float.intBitsToFloat(data[3]);
        assertEquals((float) noise.first.lowestFreqValueFactor, lowestVaF, 0.0f,
            "lowestFreqValueFactor round-trips");
    }

    /**
     * Amplitude array at data[4..4+N-1] must match first.amplitudes[].
     */
    @Test
    public void testAmplitudes() {
        double[] amps = {1.0, 0.0, 2.0};
        NormalNoise noise = makeNoise(7, -3, amps);
        int[] data = NormalNoiseGpuSerializer.toGpuData(noise);
        int N = noise.first.noiseLevels.length;
        assertEquals(3, N);
        for (int i = 0; i < N; i++) {
            float got = Float.intBitsToFloat(data[4 + i]);
            assertEquals((float) amps[i], got, 0.0f, "amplitude[" + i + "]");
        }
    }

    /**
     * The zero-amplitude octave slot must be zeroed (null noiseLevels entry → zero block).
     */
    @Test
    public void testNullOctaveIsZeroed() {
        // amplitude[1] == 0 → noiseLevels[1] == null → 68 zero uint32s
        NormalNoise noise = makeNoise(13, -2, 1.0, 0.0, 1.0);
        int[] data = NormalNoiseGpuSerializer.toGpuData(noise);
        int N = noise.first.noiseLevels.length;
        assertNull(noise.first.noiseLevels[1], "noiseLevels[1] should be null for amplitude 0");
        int firstBase = 4 + N;
        int nullBase  = firstBase + 1 * 68; // octave index 1
        for (int k = 0; k < 67; k++) { // 0..66 (67 = padding, can be anything)
            assertEquals(0, data[nullBase + k], "null octave slot[" + k + "] must be zero");
        }
    }

    /**
     * The permutation table of the first active octave must encode correctly.
     * Byte at index xi should be recoverable via (packed[xi>>2] >> ((xi&3)*8)) & 0xFF.
     */
    @Test
    public void testPermTableEncoding() {
        NormalNoise noise = makeNoise(99, -4, 1, 1, 1, 1, 1);
        int[] data = NormalNoiseGpuSerializer.toGpuData(noise);
        int N = noise.first.noiseLevels.length;
        int firstBase = 4 + N;

        // Check first non-null octave in first stack
        for (int oct = 0; oct < N; oct++) {
            if (noise.first.noiseLevels[oct] == null) continue;
            byte[] p = noise.first.noiseLevels[oct].p;
            int base = firstBase + oct * 68;
            for (int xi = 0; xi < 256; xi++) {
                int expected = p[xi] & 0xFF;
                int packed   = data[base + (xi >> 2)];
                int got      = (packed >> ((xi & 3) * 8)) & 0xFF;
                assertEquals(expected, got, "perm[" + xi + "] for octave " + oct);
            }
            break; // one octave is enough
        }
    }

    /**
     * xo/yo/zo of first non-null octave must be stored at [base+64..66].
     */
    @Test
    public void testXoYoZo() {
        NormalNoise noise = makeNoise(55, -3, 1, 1, 1, 1);
        int[] data = NormalNoiseGpuSerializer.toGpuData(noise);
        int N = noise.first.noiseLevels.length;
        int firstBase = 4 + N;

        for (int oct = 0; oct < N; oct++) {
            ImprovedNoise im = noise.first.noiseLevels[oct];
            if (im == null) continue;
            int base = firstBase + oct * 68;
            assertEquals(Float.floatToRawIntBits((float) im.xo), data[base + 64], "xo");
            assertEquals(Float.floatToRawIntBits((float) im.yo), data[base + 65], "yo");
            assertEquals(Float.floatToRawIntBits((float) im.zo), data[base + 66], "zo");
            break;
        }
    }

    /**
     * Second stack must follow immediately after first stack at data[4+N+N*68].
     * Its octave tables must differ from the first stack's (different seeding).
     */
    @Test
    public void testSecondStackOffset() {
        NormalNoise noise = makeNoise(77, -4, 1, 1, 1, 1, 1);
        int[] data = NormalNoiseGpuSerializer.toGpuData(noise);
        int N = noise.first.noiseLevels.length;
        int firstBase  = 4 + N;
        int secondBase = 4 + N + N * 68;

        // Both stacks must have the same xo/yo/zo for same-index octave... unless the seeding differs.
        // They should DIFFER since first and second are seeded independently.
        boolean anyDiff = false;
        for (int oct = 0; oct < N; oct++) {
            if (noise.first.noiseLevels[oct] == null || noise.second.noiseLevels[oct] == null) continue;
            int b1 = firstBase  + oct * 68;
            int b2 = secondBase + oct * 68;
            if (data[b1 + 64] != data[b2 + 64] || data[b1 + 65] != data[b2 + 65]) {
                anyDiff = true;
                break;
            }
        }
        assertTrue(anyDiff, "first and second stacks must have different xo/yo/zo values");
    }

    /**
     * Total array size must be 4 + N + 2*N*68.
     */
    @Test
    public void testTotalSize() {
        NormalNoise noise = makeNoise(0, -5, 1, 1, 1);
        int[] data = NormalNoiseGpuSerializer.toGpuData(noise);
        int N = noise.first.noiseLevels.length;
        assertEquals(4 + N + 2 * N * 68, data.length, "total size");
    }
}
