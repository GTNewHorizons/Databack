package databack.common.worldgen.noise;

import databack.common.dto.worldgen.density_function.OldBlendedNoise;

/**
 * Serializes an {@link OldBlendedNoise} into a {@code int[]} suitable for upload into the
 * GPU {@code constants[]} buffer.
 *
 * <h3>Layout (at {@code tableBase}):</h3>
 * <pre>
 *   [0    .. 543 ] : mainNoise     octaves, getOctaveNoise(0..7)   — 8  × 68 uint32s
 *   [544  .. 1631] : minLimitNoise octaves, getOctaveNoise(0..15)  — 16 × 68 uint32s
 *   [1632 .. 2719] : maxLimitNoise octaves, getOctaveNoise(0..15)  — 16 × 68 uint32s
 * </pre>
 *
 * Total size: 2720 uint32s.
 *
 * <p>Each octave block (68 uint32s) uses the same OBN per-octave format as
 * {@link NormalNoiseGpuSerializer}:
 * <ul>
 *   <li>{@code [base + 0..63]}: permutation table packed 4 bytes per uint32</li>
 *   <li>{@code [base + 64]}: {@code xo} as float bits</li>
 *   <li>{@code [base + 65]}: {@code yo} as float bits</li>
 *   <li>{@code [base + 66]}: {@code zo} as float bits</li>
 *   <li>{@code [base + 67]}: padding (zero)</li>
 * </ul>
 *
 * <p>Octave order matches {@link PerlinNoise#getOctaveNoise(int)} (reverse of
 * {@code noiseLevels[]}), which is the order used by {@link OldBlendedNoise#compute} and
 * the GLSL {@code sampleOldBlendedNoise} function.
 */
public final class OldBlendedNoiseGpuSerializer {

    private OldBlendedNoiseGpuSerializer() {}

    public static int[] toGpuData(OldBlendedNoise obn) {
        int[] data = new int[2720];
        writeOctaves(data,    0, obn.getMainNoise(),      8);
        writeOctaves(data,  544, obn.getMinLimitNoise(), 16);
        writeOctaves(data, 1632, obn.getMaxLimitNoise(), 16);
        return data;
    }

    private static void writeOctaves(int[] data, int offset, PerlinNoise noise, int count) {
        for (int i = 0; i < count; i++) {
            int base = offset + i * 68;
            ImprovedNoise oct = noise.getOctaveNoise(i);
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
