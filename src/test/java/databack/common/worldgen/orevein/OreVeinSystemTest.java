package databack.common.worldgen.orevein;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import databack.common.worldgen.orevein.OreVeinSystem.Kind;

/// The rules of docs/worldgen/ore-veins.md, section 3.
class OreVeinSystemTest {

    @Test
    void kindFollowsTheToggleSign() {
        assertEquals(Kind.COPPER, Kind.of(0.5));
        assertEquals(Kind.IRON, Kind.of(-0.5));
        assertEquals(Kind.IRON, Kind.of(0.0));
    }

    @Test
    void veinsStayInsideTheirBands() {
        assertFalse(OreVeinSystem.inVein(Kind.COPPER, -1, 1.0, -1));
        assertTrue(OreVeinSystem.inVein(Kind.COPPER, 0, 1.0, -1));
        assertTrue(OreVeinSystem.inVein(Kind.COPPER, 50, 1.0, -1));
        assertFalse(OreVeinSystem.inVein(Kind.COPPER, 51, 1.0, -1));

        assertFalse(OreVeinSystem.inVein(Kind.IRON, -61, -1.0, -1));
        assertTrue(OreVeinSystem.inVein(Kind.IRON, -60, -1.0, -1));
        assertTrue(OreVeinSystem.inVein(Kind.IRON, -8, -1.0, -1));
        assertFalse(OreVeinSystem.inVein(Kind.IRON, -7, -1.0, -1));
    }

    /// `toggle_*` cases: t = 0.5 gives y 11..39, t = 0.41 gives y 20..30.
    @Test
    void veinsFadeTowardsTheBandEnds() {
        assertFalse(OreVeinSystem.inVein(Kind.COPPER, 10, 0.5, -1));
        assertTrue(OreVeinSystem.inVein(Kind.COPPER, 11, 0.5, -1));
        assertTrue(OreVeinSystem.inVein(Kind.COPPER, 39, 0.5, -1));
        assertFalse(OreVeinSystem.inVein(Kind.COPPER, 40, 0.5, -1));

        assertFalse(OreVeinSystem.inVein(Kind.COPPER, 19, 0.41, -1));
        assertTrue(OreVeinSystem.inVein(Kind.COPPER, 20, 0.41, -1));
        assertFalse(OreVeinSystem.inVein(Kind.COPPER, 25, 0.4, -1));

        assertFalse(OreVeinSystem.inVein(Kind.IRON, -60, -0.6, -1));
        assertTrue(OreVeinSystem.inVein(Kind.IRON, -59, -0.6, -1));
    }

    @Test
    void ridgedMustBeNegative() {
        assertTrue(OreVeinSystem.inVein(Kind.COPPER, 25, 1.0, -0.01));
        assertFalse(OreVeinSystem.inVein(Kind.COPPER, 25, 1.0, 0));
    }

    @Test
    void oreChanceRisesWithTheToggle() {
        assertEquals(0.1, OreVeinSystem.oreChance(0.4), 1e-12);
        assertEquals(0.2, OreVeinSystem.oreChance(0.5), 1e-12);
        assertEquals(0.2, OreVeinSystem.oreChance(-0.5), 1e-12);
        assertEquals(0.3, OreVeinSystem.oreChance(0.6), 1e-12);
        assertEquals(0.3, OreVeinSystem.oreChance(1.5), 1e-12);
    }

    @Test
    void blocksOutsideVeinsStay() {
        OreVeinSystem system = OreVeinFixture.system(12345L);
        for (int x = 0; x < 64; x++) {
            assertNull(system.at(x, 25, 0, 0.3, -1, 1));
            assertNull(system.at(x, 25, 0, 1.0, 0.5, 1));
            assertNull(system.at(x, 60, 0, 1.0, -1, 1));
        }
    }

    @Test
    void blocksMatchTheKind() {
        OreVeinSystem system = OreVeinFixture.system(12345L);
        for (int x = 0; x < 256; x++) {
            VeinBlock copper = system.at(x, 25, 0, 1.0, -1, 1);
            assertTrue(copper == null || copper == VeinBlock.GRANITE || copper == VeinBlock.COPPER_ORE
                || copper == VeinBlock.RAW_COPPER_BLOCK, String.valueOf(copper));

            VeinBlock iron = system.at(x, -30, 0, -1.0, -1, 1);
            assertTrue(iron == null || iron == VeinBlock.TUFF || iron == VeinBlock.DEEPSLATE_IRON_ORE
                || iron == VeinBlock.RAW_IRON_BLOCK, String.valueOf(iron));
        }
    }

    /// `gap_*` cases: below the threshold, every would-be ore is filler.
    @Test
    void gapsTurnOreIntoFiller() {
        OreVeinSystem system = OreVeinFixture.system(12345L);
        int ores = 0;
        for (int x = 0; x < 1024; x++) {
            VeinBlock open = system.at(x, 25, 0, 1.0, -1, -0.3);
            VeinBlock gapped = system.at(x, 25, 0, 1.0, -1, -0.31);
            if (open == VeinBlock.COPPER_ORE || open == VeinBlock.RAW_COPPER_BLOCK) {
                ores++;
                assertEquals(VeinBlock.GRANITE, gapped);
            } else {
                assertEquals(open, gapped);
            }
        }
        assertNotEquals(0, ores);
    }

    @Test
    void placementIsDeterministic() {
        OreVeinSystem a = OreVeinFixture.system(12345L), b = OreVeinFixture.system(12345L);
        for (int x = 0; x < 256; x++) assertEquals(a.at(x, 25, 3, 0.7, -1, 1), b.at(x, 25, 3, 0.7, -1, 1));
    }
}
