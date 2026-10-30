package databack.common.worldgen.biome.zoom;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;

import org.junit.jupiter.api.Test;

/// The stand-ins' behaviour, as observed black-box on the 26.1 jar (or, for [ZoomMath], as the JDK specifies).
class ZoomStandInsTest {

    @Test
    void fromBlockRoundsDown() {
        assertEquals(0, ZoomQuartPos.fromBlock(3));
        assertEquals(1, ZoomQuartPos.fromBlock(4));
        assertEquals(-1, ZoomQuartPos.fromBlock(-1));
        assertEquals(-2, ZoomQuartPos.fromBlock(-5));
        assertEquals(Integer.MIN_VALUE / 4, ZoomQuartPos.fromBlock(Integer.MIN_VALUE));
    }

    @Test
    void floorSaturatesLikeACast() {
        assertEquals(-3, ZoomMth.floor(-2.5));
        assertEquals(2, ZoomMth.floor(2.5));
        assertEquals(0, ZoomMth.floor(Double.NaN));
        assertEquals(Integer.MAX_VALUE, ZoomMth.floor(1e20));
        assertEquals(Integer.MIN_VALUE, ZoomMth.floor(Double.NEGATIVE_INFINITY));
        assertEquals(6.25, ZoomMth.square(-2.5));
    }

    @Test
    void floorModMatchesTheJdk() {
        java.util.Random random = new java.util.Random(3);
        int[] divisors = { 1, 2, 3, 4, 7, 1024, 1000, -4, -1024, Integer.MIN_VALUE, Integer.MAX_VALUE, 1 << 30 };
        for (int i = 0; i < 20_000; i++) {
            long value = i < 10 ? new long[] { 0, -1, 1, Long.MIN_VALUE, Long.MAX_VALUE, -1024, 1023, -1025, 4096,
                -4097 }[i] : random.nextLong() >> random.nextInt(64);
            for (int divisor : divisors) {
                assertEquals((int) Math.floorMod(value, (long) divisor), ZoomMath.floorMod(value, divisor),
                    value + " mod " + divisor);
            }
        }
    }

    @Test
        void floorModHasTheDivisorsSign() {
        assertEquals(1, ZoomMath.floorMod(-7L, 4));
        assertEquals(-3, ZoomMath.floorMod(5L, -4));
        assertEquals(0, ZoomMath.floorMod(Long.MIN_VALUE, 2));

        Random random = new Random(1);
        for (int i = 0; i < 100_000; i++) {
            long value = random.nextLong();
            int divisor = random.nextInt();
            if (divisor == 0) continue;
            int mod = ZoomMath.floorMod(value, divisor);
            assertEquals(Math.floorMod(value, (long) divisor), mod);
            assertEquals(Long.signum(divisor) * Math.abs(mod), mod);
        }
    }
}
