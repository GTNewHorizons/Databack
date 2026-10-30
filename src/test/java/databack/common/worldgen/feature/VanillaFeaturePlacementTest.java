package databack.common.worldgen.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.util.Collections;

import net.minecraft.init.Blocks;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import databack.common.dto.worldgen.biome.DatapackBiome;
import databack.common.dto.worldgen.configured_feature.IConfiguredFeature;
import databack.common.dto.worldgen.placed_feature.PlacedFeature;
import databack.common.worldgen.density.VanillaWorldgen;
import databack.common.worldgen.feature.standin.FtBiomeFeatures;
import databack.common.worldgen.feature.standin.FtBlock;
import databack.common.worldgen.feature.standin.FtBlockFacts;
import databack.common.worldgen.feature.standin.FtBlockState;
import databack.common.worldgen.feature.standin.FtChunkGenerator;
import databack.common.worldgen.feature.standin.FtHolder;
import databack.common.worldgen.feature.world.ColumnArea;
import databack.common.worldgen.feature.world.FakeLegacyWorld;
import databack.common.worldgen.feature.world.FeatureBiomes;
import databack.common.worldgen.feature.world.LegacyBlockFacts;
import databack.common.worldgen.feature.world.LegacyStates;
import databack.common.worldgen.feature.world.PopulationLevel;
import databack.common.worldgen.feature.world.VanillaTagFacts;
import databack.common.worldgen.rng.RandomSource;
import databack.common.worldgen.rng.StandardRandom;
import databack.common.worldgen.vanilla.VanillaJar;
import databack.common.worldgen.vanilla.standin.VnBlockPos;

/// Vanilla's own features, run through the world adapter into a fake 1.7 world.
class VanillaFeaturePlacementTest {

    private static final FtChunkGenerator GENERATOR = new FtChunkGenerator() {

        @Override
        public int getMinY() {
            return 0;
        }

        @Override
        public int getGenDepth() {
            return 256;
        }

        @NotNull
        @Override
        public FtBiomeFeatures getBiomeGenerationSettings(@NotNull FtHolder biome) {
            return feature -> true;
        }
    };

    private static VanillaFeatures features;
    private static final LegacyStates states = new LegacyStates();

    @BeforeAll
    static void load() {
        features = VanillaFeatures.load(entry -> {
            try {
                return VanillaJar.classFile(entry);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        // Tags come from the vanilla pack; tests load no datapack registries
        FtBlockFacts.install(VanillaWorldgen.available() ? new VanillaTagFacts(states) : new LegacyBlockFacts(states));
    }

    private static FtBlockState state(String id) {
        return FtBlock.of(id).defaultBlockState();
    }

    private static PopulationLevel level(FakeLegacyWorld world) {
        return level(world, "minecraft:plains");
    }

    private static PopulationLevel level(FakeLegacyWorld world, String biome) {
        return new PopulationLevel(world, states, ColumnArea.ofChunks(0, 0, 1, 1), (x, y, z) -> biome,
            new FeatureBiomes(id -> VanillaWorldgen.available()
                ? VanillaWorldgen.definition("biome", id, DatapackBiome.class) : null),
            new StandardRandom(0));
    }

    private static boolean place(Object featureType, Object config, PopulationLevel level, RandomSource random,
        VnBlockPos origin) throws ReflectiveOperationException {
        Class<?> configured = features.vanillaClass("feature/ConfiguredFeature");
        Object feature = configured.getConstructors()[0].newInstance(featureType, config);
        Method place = configured.getMethod("place", databack.common.worldgen.feature.standin.FtLevel.class,
            FtChunkGenerator.class, RandomSource.class, VnBlockPos.class);
        return (boolean) place.invoke(feature, level, GENERATOR, random, origin);
    }

    @Test
    void simpleBlockPlacesItsBlock() throws ReflectiveOperationException {
        FakeLegacyWorld world = new FakeLegacyWorld(Blocks.air);
        Object provider = features.vanillaClass("feature/stateproviders/BlockStateProvider")
            .getMethod("simple", FtBlockState.class).invoke(null, state("minecraft:stone"));
        Object config = features.vanillaClass("feature/configurations/SimpleBlockConfiguration")
            .getConstructor(features.vanillaClass("feature/stateproviders/BlockStateProvider"))
            .newInstance(provider);

        assertTrue(place(features.featureType("SIMPLE_BLOCK"), config, level(world), new StandardRandom(0),
            new VnBlockPos(5, 64, 5)));
        assertSame(Blocks.stone, world.getBlock(5, 64, 5));
    }

    @Test
    void oreReplacesItsTarget() throws ReflectiveOperationException {
        FakeLegacyWorld world = new FakeLegacyWorld(Blocks.stone);
        Class<?> ruleTest = features.vanillaClass("structure/templatesystem/RuleTest");
        Object stoneTest = features.vanillaClass("structure/templatesystem/BlockMatchTest")
            .getConstructor(FtBlock.class).newInstance(FtBlock.of("minecraft:stone"));
        Object config = features.vanillaClass("feature/configurations/OreConfiguration")
            .getConstructor(ruleTest, FtBlockState.class, int.class)
            .newInstance(stoneTest, state("minecraft:diamond_ore"), 8);

        assertTrue(place(features.featureType("ORE"), config, level(world), new StandardRandom(42),
            new VnBlockPos(16, 40, 16)));
        assertTrue(world.count(Blocks.diamond_ore) > 0);
        assertEquals(0, world.count(Blocks.stone));
    }

    /// Water set into a stone wall with one open side, made flowing so 1.7 lets it run.
    @Test
    void springsFlowOutOfWalls() throws ReflectiveOperationException {
        assumeTrue(VanillaWorldgen.available(), "No extracted vanilla datapack");
        FeatureBuildContext ctx = new FeatureBuildContext(features,
            id -> VanillaWorldgen.definition("configured_feature", id, IConfiguredFeature.class),
            id -> VanillaWorldgen.definition("placed_feature", id, PlacedFeature.class));
        Object spring = ((FtHolder) ctx.configuredFeature("minecraft:spring_water")).value();
        Method place = spring.getClass().getMethod("place", databack.common.worldgen.feature.standin.FtLevel.class,
            FtChunkGenerator.class, RandomSource.class, VnBlockPos.class);

        FakeLegacyWorld world = new FakeLegacyWorld(Blocks.stone, 200, Blocks.air);
        world.setBlock(9, 40, 8, Blocks.air, 0);
        PopulationLevel level = level(world);

        assertTrue((boolean) place.invoke(spring, level, GENERATOR, new StandardRandom(0), new VnBlockPos(8, 40, 8)));
        assertSame(Blocks.flowing_water, world.getBlock(8, 40, 8));
        assertEquals(0, world.getMeta(8, 40, 8));
        assertEquals(Collections.singletonList("8 40 8 minecraft:flowing_water 0"), world.scheduled);

        assertFalse((boolean) place.invoke(spring, level, GENERATOR, new StandardRandom(0), new VnBlockPos(8, 30, 8)),
            "no open side");
    }

    /// Sugar cane and cactus columns of the configured heights; cactus flowers have no 1.7 block and are skipped.
    @Test
    void blockColumnsGrowToTheirHeight() throws ReflectiveOperationException {
        assumeTrue(VanillaWorldgen.available(), "No extracted vanilla datapack");
        FeatureBuildContext ctx = new FeatureBuildContext(features,
            id -> VanillaWorldgen.definition("configured_feature", id, IConfiguredFeature.class),
            id -> VanillaWorldgen.definition("placed_feature", id, PlacedFeature.class));

        assertColumn(ctx, "minecraft:sugar_cane", Blocks.reeds, 2, 4);
        assertColumn(ctx, "minecraft:cactus", Blocks.cactus, 1, 3);
    }

    private static void assertColumn(FeatureBuildContext ctx, String id, net.minecraft.block.Block block, int min,
        int max) throws ReflectiveOperationException {
        Object column = ((FtHolder) ctx.configuredFeature(id)).value();
        Method place = column.getClass().getMethod("place", databack.common.worldgen.feature.standin.FtLevel.class,
            FtChunkGenerator.class, RandomSource.class, VnBlockPos.class);

        for (int seed = 0; seed < 8; seed++) {
            FakeLegacyWorld world = new FakeLegacyWorld(Blocks.grass, 64, Blocks.air);
            assertTrue((boolean) place.invoke(column, level(world), GENERATOR, new StandardRandom(seed),
                new VnBlockPos(16, 64, 16)), id + " seed " + seed);

            int height = world.count(block);
            assertTrue(height >= min && height <= max, id + " height " + height + " seed " + seed);
            for (int y = 64; y < 64 + height; y++) assertSame(block, world.getBlock(16, y, 16), id + " at " + y);
        }
    }

    /// A lava lake sunk into flat stone.
    @Test
    void lavaLakesFillWithLava() throws ReflectiveOperationException {
        assumeTrue(VanillaWorldgen.available(), "No extracted vanilla datapack");
        FeatureBuildContext ctx = new FeatureBuildContext(features,
            id -> VanillaWorldgen.definition("configured_feature", id, IConfiguredFeature.class),
            id -> VanillaWorldgen.definition("placed_feature", id, PlacedFeature.class));
        Object lake = ((FtHolder) ctx.configuredFeature("minecraft:lake_lava")).value();
        Method place = lake.getClass().getMethod("place", databack.common.worldgen.feature.standin.FtLevel.class,
            FtChunkGenerator.class, RandomSource.class, VnBlockPos.class);

        int lava = 0;
        for (int seed = 0; seed < 4; seed++) {
            FakeLegacyWorld world = new FakeLegacyWorld(Blocks.stone, 64, Blocks.air);
            place.invoke(lake, level(world), GENERATOR, new StandardRandom(seed), new VnBlockPos(16, 64, 16));
            lava += world.count(Blocks.lava) + world.count(Blocks.flowing_lava);
        }
        assertTrue(lava > 0, "lava placed");
    }

    /// A stump, and usually a log lying beside it (1.7 log metas 4 and 8 lie along x and z).
    @Test
    void fallenTreesLieDown() throws ReflectiveOperationException {
        assumeTrue(VanillaWorldgen.available(), "No extracted vanilla datapack");
        FeatureBuildContext ctx = new FeatureBuildContext(features,
            id -> VanillaWorldgen.definition("configured_feature", id, IConfiguredFeature.class),
            id -> VanillaWorldgen.definition("placed_feature", id, PlacedFeature.class));
        Object fallen = ((FtHolder) ctx.configuredFeature("minecraft:fallen_oak_tree")).value();
        Method place = fallen.getClass().getMethod("place", databack.common.worldgen.feature.standin.FtLevel.class,
            FtChunkGenerator.class, RandomSource.class, VnBlockPos.class);

        int lying = 0;
        for (int seed = 0; seed < 8; seed++) {
            FakeLegacyWorld world = new FakeLegacyWorld(Blocks.grass, 64, Blocks.air);
            assertTrue((boolean) place.invoke(fallen, level(world), GENERATOR, new StandardRandom(seed),
                new VnBlockPos(16, 64, 16)), "seed " + seed);
            assertSame(Blocks.log, world.getBlock(16, 64, 16), "stump, seed " + seed);

            for (int x = 0; x < 48; x++) {
                for (int z = 0; z < 48; z++) {
                    if (world.getBlock(x, 64, z) == Blocks.log && (world.getMeta(x, 64, z) & 12) != 0) lying++;
                }
            }
        }
        assertTrue(lying > 0, "logs lying down");
    }
}
