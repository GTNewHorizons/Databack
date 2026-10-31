package databack.common.worldgen.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import databack.common.worldgen.rng.RandomSource;
import databack.common.worldgen.rng.XoroshiroRandom;
import databack.common.worldgen.vanilla.standin.VnBlockPos;

/// [FeatureBand] over configured features that record where they're placed.
class FeatureBandTest {

    private final List<Integer> placedAt = new ArrayList<>();
    private final VanillaConfiguredFeature feature = (level, generator, random, origin) -> placedAt.add(origin.getY());

    private boolean place(int y, RandomSource random) {
        return FeatureBand.place(feature, null, null, random, new VnBlockPos(0, y, 0));
    }

    @Test
    void outsideABandEverythingIsPlacedWithTheGivenRandom() {
        RandomSource random = new XoroshiroRandom(1);
        List<RandomSource> seen = new ArrayList<>();
        FeatureBand.place((VanillaConfiguredFeature) (level, generator, given, origin) -> seen.add(given), null, null,
            random, new VnBlockPos(0, 500, 0));

        assertSame(random, seen.get(0));
    }

    @Test
    void onlyPositionsInTheBandArePlaced() {
        FeatureBand.within(16, 31, () -> {
            RandomSource random = new XoroshiroRandom(1);
            assertFalse(place(15, random));
            assertTrue(place(16, random));
            assertTrue(place(31, random));
            assertFalse(place(32, random));
        });

        assertEquals(Arrays.asList(16, 31), placedAt);
    }

    /// The random goes on the same way whether a position is placed or skipped.
    @Test
    void skippingKeepsTheRandom() {
        RandomSource inBand = new XoroshiroRandom(7), outOfBand = new XoroshiroRandom(7);
        FeatureBand.within(0, 15, () -> {
            place(5, inBand);
            place(50, outOfBand);
        });

        assertEquals(inBand.nextLong(), outOfBand.nextLong());
    }

    /// A placed feature's own features go ahead wherever they are.
    @Test
    void nestedFeaturesArePlaced() {
        VanillaConfiguredFeature patch = (level, generator, random, origin) -> place(origin.getY() + 20, random);
        FeatureBand.within(0, 15, () -> FeatureBand.place(patch, null, null, new XoroshiroRandom(1),
            new VnBlockPos(0, 10, 0)));

        assertEquals(Collections.singletonList(30), placedAt);
    }

    @Test
    void bandsDoNotNest() {
        assertThrows(IllegalStateException.class, () -> FeatureBand.within(0, 15, () -> FeatureBand.within(0, 15,
            () -> {})));
    }
}
