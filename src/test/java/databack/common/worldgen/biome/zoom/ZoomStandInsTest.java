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
