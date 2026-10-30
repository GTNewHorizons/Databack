package databack.common.worldgen.feature;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.init.Blocks;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import databack.common.worldgen.biome.BiomeTemperature;
import databack.common.worldgen.feature.standin.FtBiome;
import databack.common.worldgen.feature.standin.FtBlockFacts;
import databack.common.worldgen.feature.world.ColumnArea;
import databack.common.worldgen.feature.world.FakeLegacyWorld;
import databack.common.worldgen.feature.world.FeatureBiomes;
import databack.common.worldgen.feature.world.LegacyBlockFacts;
import databack.common.worldgen.feature.world.LegacyStates;
import databack.common.worldgen.feature.world.PopulationLevel;
import databack.common.worldgen.rng.StandardRandom;
import databack.common.worldgen.vanilla.standin.VnBlockPos;

/// Where [FtBiome] freezes water and lays snow, over real 1.7 blocks.
class FtBiomeTest {

    private static final LegacyStates states = new LegacyStates();
    private static final FtBiome COLD = new FtBiome(new BiomeTemperature(0F, false));
    private static final FtBiome WARM = new FtBiome(new BiomeTemperature(0.8F, false));
    private static final VnBlockPos POS = new VnBlockPos(8, 63, 8);

    @BeforeAll
    static void facts() {
        FtBlockFacts.install(new LegacyBlockFacts(states));
    }

    private static PopulationLevel level(FakeLegacyWorld world) {
        return new PopulationLevel(world, states, ColumnArea.ofChunks(0, 0, 1, 1), (x, y, z) -> "minecraft:test",
            new FeatureBiomes(id -> null), new StandardRandom(0));
    }

    @Test
    void stillWaterFreezesWhenCold() {
        PopulationLevel level = level(new FakeLegacyWorld(Blocks.water, 64, Blocks.air));
        assertTrue(COLD.shouldFreeze(level, POS, false));
        assertFalse(WARM.shouldFreeze(level, POS, false));
    }

    /// Water surrounded by water only freezes when the edge doesn't matter.
    @Test
    void edgesFreezeFirst() {
        FakeLegacyWorld world = new FakeLegacyWorld(Blocks.water, 64, Blocks.air);
        PopulationLevel level = level(world);
        assertFalse(COLD.shouldFreeze(level, POS));

        world.setBlock(9, 63, 8, Blocks.stone, 0);
        assertTrue(COLD.shouldFreeze(level, POS));
    }

    @Test
    void flowingWaterAndOtherBlocksDontFreeze() {
        FakeLegacyWorld world = new FakeLegacyWorld(Blocks.stone, 64, Blocks.air);
        world.setBlock(8, 63, 8, Blocks.water, 3);
        PopulationLevel level = level(world);
        assertFalse(COLD.shouldFreeze(level, POS, false));
        assertFalse(COLD.shouldFreeze(level, new VnBlockPos(8, 62, 8), false));
    }

    @Test
    void snowFallsOnAirWhenCold() {
        PopulationLevel level = level(new FakeLegacyWorld(Blocks.grass, 64, Blocks.air));
        VnBlockPos above = POS.above();
        assertTrue(COLD.shouldSnow(level, above));
        assertFalse(WARM.shouldSnow(level, above));
        assertFalse(COLD.shouldSnow(level, POS), "not into the ground");
    }

    @Test
    void nothingFormsOutsideTheBuildHeight() {
        PopulationLevel level = level(new FakeLegacyWorld(Blocks.air, 0, Blocks.air));
        assertFalse(COLD.shouldSnow(level, new VnBlockPos(8, 256, 8)));
    }
}
