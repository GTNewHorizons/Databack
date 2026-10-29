package databack.common.worldgen.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.util.Collections;

import net.minecraft.init.Blocks;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import databack.common.worldgen.feature.standin.FtBiomeFeatures;
import databack.common.worldgen.feature.standin.FtBlock;
import databack.common.worldgen.feature.standin.FtBlockFacts;
import databack.common.worldgen.feature.standin.FtBlockState;
import databack.common.worldgen.feature.standin.FtChunkGenerator;
import databack.common.worldgen.feature.standin.FtHolder;
import databack.common.worldgen.feature.world.ColumnArea;
import databack.common.worldgen.feature.world.FakeLegacyWorld;
import databack.common.worldgen.feature.world.LegacyBlockFacts;
import databack.common.worldgen.feature.world.LegacyStates;
import databack.common.worldgen.feature.world.PopulationLevel;
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
        FtBlockFacts.install(new LegacyBlockFacts(states));
    }

    private static FtBlockState state(String id) {
        return FtBlock.of(id).defaultBlockState();
    }

    private static PopulationLevel level(FakeLegacyWorld world) {
        return new PopulationLevel(world, states, ColumnArea.ofChunks(0, 0, 1, 1), (x, y, z) -> "minecraft:plains",
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
}
