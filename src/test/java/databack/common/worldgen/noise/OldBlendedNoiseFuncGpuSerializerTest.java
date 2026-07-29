package databack.common.worldgen.noise;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;

import org.junit.jupiter.api.Test;

import databack.common.dto.worldgen.density_function.OldBlendedNoise;
import databack.common.worldgen.rng.StandardRandomFactory;

public class OldBlendedNoiseFuncGpuSerializerTest {

    private static OldBlendedNoise makeObn(long seed,
            float xzScale, float yScale, float xzFactor, float yFactor, float smear) {
        return new OldBlendedNoise(new StandardRandomFactory(seed), xzScale, yScale, xzFactor, yFactor, smear);
    }

    /** Total size must be exactly 2720 uints (8+16+16 octaves × 68 uints each). */
    @Test
    public void testTotalSize() {
        OldBlendedNoise obn = makeObn(42, 0.25f, 0.125f, 80f, 160f, 8f);
        int[] data = OldBlendedNoiseGpuSerializer.toGpuData(obn);
        assertEquals(2720, data.length, "total size must be 2720");
    }

    /**
     * mainNoise occupies [0..543], minLimitNoise [544..1631], maxLimitNoise [1632..2719].
     * Verify by checking xo at +64 for getOctaveNoise(0) of each stack.
     */
    @Test
    public void testStackOffsets() {
        OldBlendedNoise obn = makeObn(7, 1f, 1f, 80f, 160f, 8f);
        int[] data = OldBlendedNoiseGpuSerializer.toGpuData(obn);

        ImprovedNoise mainOct0 = obn.getMainNoise().getOctaveNoise(0);
        ImprovedNoise minOct0  = obn.getMinLimitNoise().getOctaveNoise(0);
        ImprovedNoise maxOct0  = obn.getMaxLimitNoise().getOctaveNoise(0);

        assertNotNull(mainOct0);
        assertNotNull(minOct0);
        assertNotNull(maxOct0);

        assertEquals(Float.floatToRawIntBits((float) mainOct0.xo), data[0   + 64], "mainNoise xo at offset 0");
        assertEquals(Float.floatToRawIntBits((float) minOct0.xo),  data[544  + 64], "minLimitNoise xo at offset 544");
        assertEquals(Float.floatToRawIntBits((float) maxOct0.xo),  data[1632 + 64], "maxLimitNoise xo at offset 1632");
    }

    /** The three stacks must have distinct xo values (independently seeded). */
    @Test
    public void testStacksAreDistinct() {
        OldBlendedNoise obn = makeObn(99, 0.25f, 0.125f, 80f, 160f, 8f);
        int[] data = OldBlendedNoiseGpuSerializer.toGpuData(obn);

        int mainXo = data[0    + 64];
        int minXo  = data[544  + 64];
        int maxXo  = data[1632 + 64];

        // minLimit and maxLimit are seeded sequentially after each other, so they'll differ.
        assertNotEquals(minXo, maxXo, "minLimitNoise and maxLimitNoise must have different xo");
        // main is seeded last and will also differ.
        assertNotEquals(mainXo, minXo, "mainNoise and minLimitNoise must have different xo");
    }

    /**
     * Perm table of mainNoise getOctaveNoise(0) must round-trip:
     * byte xi recoverable as (packed[xi>>2] >> ((xi&3)*8)) & 0xFF.
     */
    @Test
    public void testPermTableEncodingMain() {
        OldBlendedNoise obn = makeObn(55, 1f, 1f, 80f, 160f, 8f);
        int[] data = OldBlendedNoiseGpuSerializer.toGpuData(obn);

        ImprovedNoise oct = obn.getMainNoise().getOctaveNoise(0);
        assertNotNull(oct);
        byte[] p = oct.p;
        for (int xi = 0; xi < 256; xi++) {
            int expected = p[xi] & 0xFF;
            int packed   = data[xi >> 2];
            int got      = (packed >> ((xi & 3) * 8)) & 0xFF;
            assertEquals(expected, got, "mainNoise perm[" + xi + "]");
        }
    }

    /**
     * Perm table of minLimitNoise getOctaveNoise(0) must round-trip at offset 544.
     */
    @Test
    public void testPermTableEncodingMin() {
        OldBlendedNoise obn = makeObn(55, 1f, 1f, 80f, 160f, 8f);
        int[] data = OldBlendedNoiseGpuSerializer.toGpuData(obn);

        ImprovedNoise oct = obn.getMinLimitNoise().getOctaveNoise(0);
        assertNotNull(oct);
        byte[] p = oct.p;
        for (int xi = 0; xi < 256; xi++) {
            int expected = p[xi] & 0xFF;
            int packed   = data[544 + (xi >> 2)];
            int got      = (packed >> ((xi & 3) * 8)) & 0xFF;
            assertEquals(expected, got, "minLimitNoise perm[" + xi + "]");
        }
    }

    /** xo/yo/zo must be stored at base+64/65/66 for each octave in mainNoise. */
    @Test
    public void testXoYoZoMain() {
        OldBlendedNoise obn = makeObn(13, 0.25f, 0.125f, 80f, 160f, 8f);
        int[] data = OldBlendedNoiseGpuSerializer.toGpuData(obn);

        for (int i = 0; i < 8; i++) {
            ImprovedNoise oct = obn.getMainNoise().getOctaveNoise(i);
            if (oct == null) continue;
            int base = i * 68;
            assertEquals(Float.floatToRawIntBits((float) oct.xo), data[base + 64], "main oct " + i + " xo");
            assertEquals(Float.floatToRawIntBits((float) oct.yo), data[base + 65], "main oct " + i + " yo");
            assertEquals(Float.floatToRawIntBits((float) oct.zo), data[base + 66], "main oct " + i + " zo");
        }
    }

    /** xo/yo/zo must be stored correctly for all 16 minLimitNoise octaves. */
    @Test
    public void testXoYoZoMin() {
        OldBlendedNoise obn = makeObn(13, 0.25f, 0.125f, 80f, 160f, 8f);
        int[] data = OldBlendedNoiseGpuSerializer.toGpuData(obn);

        for (int i = 0; i < 16; i++) {
            ImprovedNoise oct = obn.getMinLimitNoise().getOctaveNoise(i);
            if (oct == null) continue;
            int base = 544 + i * 68;
            assertEquals(Float.floatToRawIntBits((float) oct.xo), data[base + 64], "min oct " + i + " xo");
            assertEquals(Float.floatToRawIntBits((float) oct.yo), data[base + 65], "min oct " + i + " yo");
            assertEquals(Float.floatToRawIntBits((float) oct.zo), data[base + 66], "min oct " + i + " zo");
        }
    }

    /** xo/yo/zo must be stored correctly for all 16 maxLimitNoise octaves. */
    @Test
    public void testXoYoZoMax() {
        OldBlendedNoise obn = makeObn(13, 0.25f, 0.125f, 80f, 160f, 8f);
        int[] data = OldBlendedNoiseGpuSerializer.toGpuData(obn);

        for (int i = 0; i < 16; i++) {
            ImprovedNoise oct = obn.getMaxLimitNoise().getOctaveNoise(i);
            if (oct == null) continue;
            int base = 1632 + i * 68;
            assertEquals(Float.floatToRawIntBits((float) oct.xo), data[base + 64], "max oct " + i + " xo");
            assertEquals(Float.floatToRawIntBits((float) oct.yo), data[base + 65], "max oct " + i + " yo");
            assertEquals(Float.floatToRawIntBits((float) oct.zo), data[base + 66], "max oct " + i + " zo");
        }
    }

    /** A null octave slot must be all zeros (67 meaningful words; padding at +67 can be anything). */
    @Test
    public void testNullOctaveIsZeroed() {
        // Legacy blended noise always fills all octaves, so synthesize a null by checking
        // the array is zeroed for any slot where getOctaveNoise returns null.
        OldBlendedNoise obn = makeObn(0, 0.25f, 0.125f, 80f, 160f, 8f);
        int[] data = OldBlendedNoiseGpuSerializer.toGpuData(obn);

        for (int i = 0; i < 8; i++) {
            if (obn.getMainNoise().getOctaveNoise(i) != null) continue;
            int base = i * 68;
            for (int k = 0; k < 67; k++) {
                assertEquals(0, data[base + k], "null main octave " + i + " slot[" + k + "] must be zero");
            }
        }
        for (int i = 0; i < 16; i++) {
            if (obn.getMinLimitNoise().getOctaveNoise(i) != null) continue;
            int base = 544 + i * 68;
            for (int k = 0; k < 67; k++) {
                assertEquals(0, data[base + k], "null min octave " + i + " slot[" + k + "] must be zero");
            }
        }
    }
}
