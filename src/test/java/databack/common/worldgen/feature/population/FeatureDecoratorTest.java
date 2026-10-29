package databack.common.worldgen.feature.population;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import databack.common.dto.worldgen.biome.DatapackBiome;
import databack.common.interop.registry.BlockIdentityRegistry;
import databack.common.dto.worldgen.configured_feature.IConfiguredFeature;
import databack.common.dto.worldgen.placed_feature.PlacedFeature;
import databack.common.worldgen.density.VanillaWorldgen;
import databack.common.worldgen.feature.BiomeFeatures;
import databack.common.worldgen.feature.FeatureBuildContext;
import databack.common.worldgen.feature.VanillaFeatures;
import databack.common.worldgen.feature.standin.FtBlockFacts;
import databack.common.worldgen.feature.world.FakeLegacyWorld;
import databack.common.worldgen.feature.world.VanillaTagFacts;
import databack.common.worldgen.feature.world.LegacyStates;
import databack.common.worldgen.feature.world.PopulationLevel;
import databack.common.worldgen.rng.StandardRandom;
import databack.common.worldgen.vanilla.VanillaJar;

/// Plains' own features from the vanilla datapack, decorating a fake world of stone.
class FeatureDecoratorTest {

    private static VanillaFeatures vanilla;
    private static BiomeFeatures plains;
    private static final LegacyStates states = new LegacyStates();

    @BeforeAll
    static void build() {
        assumeTrue(VanillaWorldgen.available(), "No extracted vanilla datapack");
        vanilla = VanillaFeatures.load(entry -> {
            try {
                return VanillaJar.classFile(entry);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        FtBlockFacts.install(new VanillaTagFacts(states));

        FeatureBuildContext ctx = new FeatureBuildContext(vanilla,
            id -> VanillaWorldgen.definition("configured_feature", id, IConfiguredFeature.class),
            id -> VanillaWorldgen.definition("placed_feature", id, PlacedFeature.class));
        plains = new BiomeFeatures(ctx, Collections.singleton("minecraft:plains"),
            id -> VanillaWorldgen.definition("biome", id, DatapackBiome.class), -64, 384);
    }

    private static FakeLegacyWorld decorate(int chunkX, int chunkZ) {
        // Solid to the top: ores only form below the surface.
        FakeLegacyWorld world = new FakeLegacyWorld(Blocks.stone, 256);
        List<long[]> loaded = new ArrayList<>();
        FeatureDecorator decorator = new FeatureDecorator((x, z) -> loaded.add(new long[] { x, z }),
            area -> new PopulationLevel(world, states, area, (x, y, z) -> "minecraft:plains", new StandardRandom(0)),
            vanilla, plains, 1234L);

        decorator.decorate(chunkX, chunkZ);
        assertEquals(9, loaded.size());
        return world;
    }

    @Test
    void plainsGetTheirOres() {
        FakeLegacyWorld world = decorate(0, 0);
        for (Block ore : new Block[] { Blocks.coal_ore, Blocks.iron_ore, Blocks.dirt, Blocks.gravel }) {
            assertTrue(world.count(ore) > 0, ore.getUnlocalizedName() + " placed");
        }
    }

    /// Et Futurum's stones stand in for granite, diorite and andesite.
    @Test
    void stoneBlobsUseEtFuturumsStones() {
        Block granite = BlockIdentityRegistry.INSTANCE.getObject(new ResourceLocation("minecraft:granite")).block;
        assertTrue(decorate(0, 0).count(granite) > 0);
    }

    /// Nothing is carved out: blocks without a 1.7 counterpart are skipped, not written as air.
    @Test
    void noBlobLeavesAir() {
        assertEquals(0, decorate(0, 0).count(Blocks.air));
    }

    @Test
    void decorationIsDeterministic() {
        assertEquals(decorate(3, -2).count(Blocks.coal_ore), decorate(3, -2).count(Blocks.coal_ore));
    }
}
