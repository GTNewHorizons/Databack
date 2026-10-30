package databack.common.worldgen.feature.population;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
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
import databack.common.worldgen.feature.world.FeatureBiomes;
import databack.common.worldgen.feature.world.VanillaTagFacts;
import databack.common.worldgen.feature.world.LegacyStates;
import databack.common.worldgen.feature.world.PopulationLevel;
import databack.common.worldgen.rng.StandardRandom;
import databack.common.worldgen.vanilla.VanillaJar;

/// Plains' own features from the vanilla datapack, decorating a fake world of stone.
class FeatureDecoratorTest {

    private static VanillaFeatures vanilla;
    private static BiomeFeatures plains, forest, snowyPlains, frozenRiver, desert;
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
        forest = new BiomeFeatures(ctx, Collections.singleton("minecraft:forest"),
            id -> VanillaWorldgen.definition("biome", id, DatapackBiome.class), -64, 384);
        snowyPlains = new BiomeFeatures(ctx, Collections.singleton("minecraft:snowy_plains"),
            id -> VanillaWorldgen.definition("biome", id, DatapackBiome.class), -64, 384);
        frozenRiver = new BiomeFeatures(ctx, Collections.singleton("minecraft:frozen_river"),
            id -> VanillaWorldgen.definition("biome", id, DatapackBiome.class), -64, 384);
        desert = new BiomeFeatures(ctx, Collections.singleton("minecraft:desert"),
            id -> VanillaWorldgen.definition("biome", id, DatapackBiome.class), -64, 384);
    }

    /// Solid to the top: ores only form below the surface.
    private static FakeLegacyWorld decorate(int chunkX, int chunkZ) {
        return decorate(new FakeLegacyWorld(Blocks.stone, 256), chunkX, chunkZ);
    }

    private static FakeLegacyWorld decorate(FakeLegacyWorld world, int chunkX, int chunkZ) {
        return decorate(world, plains, "minecraft:plains", chunkX, chunkZ);
    }

    private static FakeLegacyWorld decorate(FakeLegacyWorld world, BiomeFeatures features, String biome, int chunkX,
        int chunkZ) {
        List<long[]> loaded = new ArrayList<>();
        FeatureDecorator decorator = new FeatureDecorator((x, z) -> loaded.add(new long[] { x, z }),
            area -> new PopulationLevel(world, states, area, (x, y, z) -> biome,
                new FeatureBiomes(id -> VanillaWorldgen.definition("biome", id, DatapackBiome.class)),
                new StandardRandom(0)),
            (x, z, into) -> into.add(biome), vanilla, features, 1234L);

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

    /// Diamonds count from the dimension's bottom (y = -64), not the 1.7 world's: their veins center at y <= 16, so
    /// none reach y = 33.
    @Test
    void diamondsStayDeep() {
        int diamonds = 0;
        for (int chunk = 0; chunk < 16; chunk++) {
            int chunkX = chunk % 4, chunkZ = chunk / 4;
            FakeLegacyWorld world = decorate(chunkX, chunkZ);
            for (int x = (chunkX - 1) * 16; x < (chunkX + 2) * 16; x++) {
                for (int z = (chunkZ - 1) * 16; z < (chunkZ + 2) * 16; z++) {
                    for (int y = 33; y < 256; y++) {
                        assertNotSame(Blocks.diamond_ore, world.getBlock(x, y, z), "diamond at y = " + y);
                    }
                }
            }
            diamonds += world.count(Blocks.diamond_ore);
        }
        assertTrue(diamonds > 0, "diamonds placed");
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

    /// Grass and flower counts follow the biome info noise. Plains flowers come in one chunk in 32.
    @Test
    void plainsGetTheirVegetation() {
        int grass = 0, flowers = 0;
        for (int chunk = 0; chunk < 64; chunk++) {
            FakeLegacyWorld world = decorate(new FakeLegacyWorld(Blocks.air, 64), chunk % 8, chunk / 8);
            grass += world.count(Blocks.tallgrass) + world.count(Blocks.double_plant);
            flowers += world.count(Blocks.red_flower) + world.count(Blocks.yellow_flower);
        }
        assertTrue(grass > 0, "grass placed");
        assertTrue(flowers > 0, "flowers placed");
    }

    @Test
    void forestsGetTrees() {
        FakeLegacyWorld world = decorate(new FakeLegacyWorld(Blocks.air, 64), forest, "minecraft:forest", 0, 0);
        assertTrue(world.count(Blocks.log) > 0, "oak and birch logs placed");
        assertTrue(world.count(Blocks.leaves) > 0, "oak and birch leaves placed");
    }

    @Test
    void snowyPlainsGetSnow() {
        FakeLegacyWorld world = decorate(new FakeLegacyWorld(Blocks.grass, 64, Blocks.air), snowyPlains,
            "minecraft:snowy_plains", 0, 0);
        assertTrue(world.count(Blocks.snow_layer) >= 200, "most columns snowed on");
    }

    @Test
    void frozenRiversFreeze() {
        FakeLegacyWorld world = decorate(new FakeLegacyWorld(Blocks.water, 64, Blocks.air), frozenRiver,
            "minecraft:frozen_river", 0, 0);
        assertTrue(world.count(Blocks.ice) >= 200, "most columns frozen");
    }

    /// Desert cactus patches are rare (one chunk in 13), so over 32 chunks some appear.
    @Test
    void desertsGetCactus() {
        int cactus = 0;
        for (int chunk = 0; chunk < 32; chunk++) {
            FakeLegacyWorld world = decorate(new FakeLegacyWorld(Blocks.sand, 64, Blocks.air), desert,
                "minecraft:desert", chunk % 8, chunk / 8);
            cactus += world.count(Blocks.cactus);
        }
        assertTrue(cactus > 0, "cactus placed");
    }

    @Test
    void plainsStayUnfrozen() {
        FakeLegacyWorld world = decorate(new FakeLegacyWorld(Blocks.water, 64, Blocks.air), plains,
            "minecraft:plains", 0, 0);
        assertEquals(0, world.count(Blocks.ice));
        assertEquals(0, world.count(Blocks.snow_layer));
    }

    @Test
    void decorationIsDeterministic() {
        assertEquals(decorate(3, -2).count(Blocks.coal_ore), decorate(3, -2).count(Blocks.coal_ore));
    }
}
