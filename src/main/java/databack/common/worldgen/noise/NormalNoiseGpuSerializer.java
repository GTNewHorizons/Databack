package databack.common.worldgen.noise;

/**
 * Serializes a {@link NormalNoise} into a {@code int[]} suitable for upload into the
 * GPU {@code constants[]} buffer.
 *
 * <h3>Layout (at {@code tableBase}):</h3>
 * <pre>
 *   [0]                  : valueFactor              (IEEE 754 float bits)
 *   [1]                  : numOctaves N             (uint32)
 *   [2]                  : lowestFreqInputFactor     (IEEE 754 float bits, = 2^firstOctave)
 *   [3]                  : lowestFreqValueFactor     (IEEE 754 float bits, = 2^(N-1)/(2^N-1))
 *   [4 .. 4+N-1]         : amplitudes[0..N-1]        (IEEE 754 float bits; 0.0 = skip)
 *   [4+N .. 4+N+N*68-1]  : first.noiseLevels[0..N-1] (68 uint32s each)
 *   [4+N+N*68 .. end]    : second.noiseLevels[0..N-1] (68 uint32s each)
 * </pre>
 *
 * <p>Total size: {@code 4 + N + 2*N*68 = 4 + 137*N} uint32s.
 *
 * <p>Each octave block (68 uint32s) matches the OBN per-octave format:
 * <ul>
 *   <li>{@code [base + 0..63]}: permutation table packed 4 bytes per uint32
 *       ({@code p[4k] | p[4k+1]<<8 | p[4k+2]<<16 | p[4k+3]<<24})</li>
 *   <li>{@code [base + 64]}: {@code xo} as float bits</li>
 *   <li>{@code [base + 65]}: {@code yo} as float bits</li>
 *   <li>{@code [base + 66]}: {@code zo} as float bits</li>
 *   <li>{@code [base + 67]}: padding (zero)</li>
 * </ul>
 *
 * <p>Null octave slots (amplitude == 0) still occupy their 68 uint32 slots (zeroed);
 * the GLSL loop skips them via {@code if (amp != 0.0f)}.
 */
public final class NormalNoiseGpuSerializer {

    private NormalNoiseGpuSerializer() {}

    public static int[] toGpuData(NormalNoise noise) {
        PerlinNoise first = noise.first;
        PerlinNoise second = noise.second;
        int N = first.noiseLevels.length;
        int[] data = new int[4 + N + 2 * N * 68];

        data[0] = Float.floatToRawIntBits((float) noise.valueFactor);
        data[1] = N;
        data[2] = Float.floatToRawIntBits((float) first.lowestFreqInputFactor);
        data[3] = Float.floatToRawIntBits((float) first.lowestFreqValueFactor);

        for (int i = 0; i < N; i++) {
            data[4 + i] = Float.floatToRawIntBits((float) first.amplitudes[i]);
        }

        writeStack(data, 4 + N, first.noiseLevels, N);
        writeStack(data, 4 + N + N * 68, second.noiseLevels, N);

        return data;
    }

    private static void writeStack(int[] data, int offset, ImprovedNoise[] levels, int N) {
        for (int i = 0; i < N; i++) {
            int base = offset + i * 68;
            ImprovedNoise oct = levels[i];
            if (oct == null) continue; // leave as zeros
            byte[] p = oct.p;
            for (int k = 0; k < 64; k++) {
                int b0 = p[k * 4]     & 0xFF;
                int b1 = p[k * 4 + 1] & 0xFF;
                int b2 = p[k * 4 + 2] & 0xFF;
                int b3 = p[k * 4 + 3] & 0xFF;
                data[base + k] = b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
            }
            data[base + 64] = Float.floatToRawIntBits((float) oct.xo);
            data[base + 65] = Float.floatToRawIntBits((float) oct.yo);
            data[base + 66] = Float.floatToRawIntBits((float) oct.zo);
            // data[base + 67] = 0 (padding)
        }
    }
}
