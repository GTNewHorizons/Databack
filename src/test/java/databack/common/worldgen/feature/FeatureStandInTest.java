package databack.common.worldgen.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import databack.common.interop.heightmap.HeightmapType;
import databack.common.worldgen.feature.standin.FtBlock;
import databack.common.worldgen.feature.standin.FtBlockState;
import databack.common.worldgen.feature.standin.FtChunkGenerator;
import databack.common.worldgen.feature.standin.FtDoublePlantBlock;
import databack.common.worldgen.feature.standin.FtFluidState;
import databack.common.worldgen.feature.standin.FtGenerationContext;
import databack.common.worldgen.feature.standin.FtHolder;
import databack.common.worldgen.feature.standin.FtHolderSet;
import databack.common.worldgen.feature.standin.FtLevel;
import databack.common.worldgen.feature.standin.FtProperty;
import databack.common.worldgen.feature.standin.FtRotatedPillarBlock;
import databack.common.worldgen.feature.standin.FtSection;
import databack.common.worldgen.feature.standin.FtSectionAccess;
import databack.common.worldgen.rng.RandomSource;
import databack.common.worldgen.rng.StandardRandom;
import databack.common.worldgen.vanilla.standin.VnAxis;
import databack.common.worldgen.vanilla.standin.VnBlockPos;
import databack.common.worldgen.vanilla.standin.VnDirection;

/// The feature stand-ins' own behaviour.
class FeatureStandInTest {

    private static FtBlockState state(String id, String... properties) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < properties.length; i += 2) map.put(properties[i], properties[i + 1]);
        return FtBlockState.of(FtBlock.of(id), map);
    }

    @Test
    void blocksAndStatesAreUnique() {
        assertSame(FtBlock.of("minecraft:stone"), FtBlock.of("minecraft:stone"));
        assertSame(state("minecraft:oak_log", "axis", "y"), state("minecraft:oak_log", "axis", "y"));
        assertSame(FtBlock.of("minecraft:stone").defaultBlockState(), state("minecraft:stone"));
    }

    @Test
    void settingValuesGivesTheMatchingState() {
        FtBlockState log = state("minecraft:oak_log", "axis", "y");

        assertSame(state("minecraft:oak_log", "axis", "x"), log.setValue(FtRotatedPillarBlock.AXIS, VnAxis.X));
        assertSame(state("minecraft:oak_log", "axis", "z"), log.trySetValue(FtRotatedPillarBlock.AXIS, VnAxis.Z));
        assertSame(state("minecraft:wheat", "age", "3"), state("minecraft:wheat").setValue(FtProperty.of("age"), 3));
        assertSame(state("minecraft:stone"), state("minecraft:stone").trySetValue(FtRotatedPillarBlock.AXIS, VnAxis.X));
    }

    @Test
    void propertiesAreTheGivenOnes() {
        FtBlockState log = state("minecraft:oak_log", "axis", "y");

        assertTrue(log.hasProperty(FtRotatedPillarBlock.AXIS));
        assertFalse(state("minecraft:stone").hasProperty(FtRotatedPillarBlock.AXIS));
        assertEquals(Collections.singletonList(FtRotatedPillarBlock.AXIS), log.getProperties());
        assertThrows(UnsupportedOperationException.class, () -> FtRotatedPillarBlock.AXIS.getPossibleValues());
    }

    @Test
    void fluidsComeFromFluidBlocksAndWaterlogging() {
        FtHolderSet water = FtHolderSet.of(Collections.singletonList(new FtHolder("minecraft:water", "water")));

        assertTrue(state("minecraft:water").getFluidState().is(water));
        assertTrue(state("minecraft:oak_stairs", "waterlogged", "true").getFluidState().is(water));
        assertFalse(state("minecraft:oak_stairs", "waterlogged", "false").getFluidState().is(water));
        assertSame(FtFluidState.LAVA, state("minecraft:lava").getFluidState());
        assertTrue(state("minecraft:stone").getFluidState().isEmpty());
    }

    @Test
    void airIsTheThreeAirs() {
        assertTrue(state("minecraft:air").isAir());
        assertTrue(state("minecraft:cave_air").isAir());
        assertTrue(state("minecraft:void_air").isAir());
        assertFalse(state("minecraft:glass").isAir());
    }

    @Test
    void holderSetsListOrOnlyMatch() {
        FtHolderSet listed = FtHolderSet.direct(FtBlock::builtInRegistryHolder,
            Arrays.asList(FtBlock.of("minecraft:stone"), FtBlock.of("minecraft:dirt")));
        assertEquals(2, listed.size());
        assertSame(FtBlock.of("minecraft:dirt"), listed.get(1).value());
        assertTrue(state("minecraft:stone").is(listed));
        assertFalse(state("minecraft:sand").is(listed));

        FtHolderSet matching = FtHolderSet.matching("#minecraft:sand", id -> id.endsWith("sand"));
        assertTrue(state("minecraft:red_sand").is(matching));
        assertThrows(UnsupportedOperationException.class, matching::size);
    }

    @Test
    void doublePlantsPlaceBothHalves() {
        FakeLevel level = new FakeLevel(-64, 384);
        FtBlockState grass = state("minecraft:tall_grass");
        assertInstanceOf(FtDoublePlantBlock.class, grass.getBlock());

        FtDoublePlantBlock.placeAt(level, grass, new VnBlockPos(1, 70, 2), 2);

        assertSame(state("minecraft:tall_grass", "half", "lower"), level.getBlockState(new VnBlockPos(1, 70, 2)));
        assertSame(state("minecraft:tall_grass", "half", "upper"), level.getBlockState(new VnBlockPos(1, 71, 2)));
    }

    @Test
    void sectionsAddressTheirOwnBlocks() {
        FakeLevel level = new FakeLevel(-64, 384);
        FtSectionAccess access = new FtSectionAccess(level);
        FtSection section = access.getSection(new VnBlockPos(-3, -60, 37));

        FtBlockState old = section.setBlockState(13, 4, 5, state("minecraft:diamond_ore"), false);

        assertSame(state("minecraft:air"), old);
        assertSame(state("minecraft:diamond_ore"), level.getBlockState(new VnBlockPos(-3, -60, 37)));
        assertSame(state("minecraft:diamond_ore"), section.getBlockState(13, 4, 5));
        assertNull(access.getSection(new VnBlockPos(0, 320, 0)));
    }

    @Test
    void generationContextIsWhatBothHave() {
        FtChunkGenerator generator = new FtChunkGenerator() {

            @Override
            public int getMinY() {
                return -64;
            }

            @Override
            public int getGenDepth() {
                return 384;
            }

            @NotNull
            @Override
            public databack.common.worldgen.feature.standin.FtBiomeFeatures getBiomeGenerationSettings(
                @NotNull FtHolder biome
            ) {
                return feature -> false;
            }
        };

        FtGenerationContext context = new FtGenerationContext(generator, new FakeLevel(0, 256));
        assertEquals(0, context.getMinGenY());
        assertEquals(256, context.getGenDepth());
    }

    /// Air everywhere it was not set.
    private static final class FakeLevel implements FtLevel {

        private final int minY, height;
        private final Map<VnBlockPos, FtBlockState> blocks = new HashMap<>();

        FakeLevel(int minY, int height) {
            this.minY = minY;
            this.height = height;
        }

        @Override
        public int getMinY() {
            return minY;
        }

        @Override
        public int getHeight() {
            return height;
        }

        @Override
        public boolean ensureCanWrite(@NotNull VnBlockPos pos) {
            return true;
        }

        @NotNull
        @Override
        public FtBlockState getBlockState(@NotNull VnBlockPos pos) {
            FtBlockState state = blocks.get(pos);
            return state != null ? state : FtBlock.of("minecraft:air").defaultBlockState();
        }

        @Override
        public boolean setBlock(@NotNull VnBlockPos pos, @NotNull FtBlockState state, int flags) {
            blocks.put(pos.immutable(), state);
            return true;
        }

        @Override
        public int getHeight(@NotNull HeightmapType heightmap, int x, int z) {
            return minY;
        }

        @NotNull
        @Override
        public FtHolder getBiome(@NotNull VnBlockPos pos) {
            return new FtHolder("minecraft:plains", "minecraft:plains");
        }

        @NotNull
        @Override
        public RandomSource getRandom() {
            return new StandardRandom(0);
        }

        @Override
        public void scheduleTick(@NotNull VnBlockPos pos, @NotNull FtBlock block, int delay) {}

        @Override
        public boolean canSurvive(@NotNull FtBlockState state, @NotNull VnBlockPos pos) {
            return true;
        }

        @Override
        public boolean isFaceSturdy(@NotNull FtBlockState state, @NotNull VnBlockPos pos, @NotNull VnDirection face) {
            return true;
        }
    }
}
