package databack.test;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import databack.common.dto.worldgen.biome.BuiltinBiomeAttributes;
import databack.common.dto.worldgen.biome.BuiltinBiomes;
import databack.common.dto.worldgen.biome.DatapackBiome;
import databack.common.dto.worldgen.block_predicate.BuiltinBlockPredicates;
import databack.common.dto.worldgen.carver.BuiltinCarvers;
import databack.common.dto.worldgen.configured_feature.BuiltinConfiguredFeatures;
import databack.common.dto.worldgen.configured_feature.IConfiguredFeature;
import databack.common.dto.worldgen.density_function.BuiltinDensityFunctions;
import databack.common.dto.worldgen.height_provider.BuiltinHeightProviders;
import databack.common.dto.worldgen.int_provider.BuiltinIntProviders;
import databack.common.dto.worldgen.noise_settings.NoiseGeneratorSettings;
import databack.common.dto.worldgen.placed_feature.BuiltinPlacementModifiers;
import databack.common.dto.worldgen.placed_feature.PlacedFeature;
import databack.common.dto.particle.BuiltinParticles;
import databack.common.dto.dimension_type.DimensionType;
import databack.common.handlers.BiomeList;
import databack.common.handlers.ConfiguredCarverList;
import databack.common.handlers.ConfiguredFeatureList;
import databack.common.handlers.DatapackHandlerRegistry;
import databack.common.handlers.DatapackNoiseList;
import databack.common.handlers.DensityFunctionList;
import databack.common.handlers.DimensionTypeList;
import databack.common.handlers.NoiseSettingsList;
import databack.common.handlers.PlacedFeatureList;
import databack.common.loader.Datapack;
import databack.common.loader.DatapackLoader;
import databack.common.loader.DatapackWorldInfo;
import databack.common.serde.DatapackSerialization;
import databack.common.serde.MiscAdapters;

/**
 * DTO-level parsing tests using the real type handlers and the vanilla datapack.
 *
 * <p>The entire pack is loaded once in {@link #loadVanillaPack()} so individual
 * tests can assert specific field values without repeating the load.
 * All tests are skipped automatically when {@code misc/test-packs/minecraft} is absent.
 */
class DtoParsingTest {

    static final File VANILLA_PACK = new File("misc/test-packs/minecraft");

    // Handler instances — populated in loadVanillaPack(), read by @Test methods.
    static BiomeList biomeList;
    static DimensionTypeList dimensionTypeList;
    static NoiseSettingsList noiseSettingsList;
    static PlacedFeatureList placedFeatureList;
    static ConfiguredFeatureList configuredFeatureList;

    @BeforeAll
    static void loadVanillaPack() throws IOException {
        assumeTrue(VANILLA_PACK.exists(), "Vanilla pack absent; copy it to misc/test-packs/minecraft");

        // Serde must be initialized before any handler is registered.
        DatapackSerialization.resetForTesting();
        DatapackSerialization.init();
        MiscAdapters.init();
        BuiltinCarvers.init();
        BuiltinBlockPredicates.init();
        BuiltinConfiguredFeatures.init();
        BuiltinDensityFunctions.init();
        BuiltinHeightProviders.init();
        BuiltinIntProviders.init();
        BuiltinParticles.init();
        BuiltinPlacementModifiers.init();
        BuiltinBiomeAttributes.init();
        BuiltinBiomes.init();
        NoiseGeneratorSettings.init();
        DatapackSerialization.finish();

        DatapackHandlerRegistry.clearForTesting();

        biomeList = new BiomeList();
        dimensionTypeList = new DimensionTypeList();
        noiseSettingsList = new NoiseSettingsList();
        placedFeatureList = new PlacedFeatureList();
        configuredFeatureList = new ConfiguredFeatureList();

        DatapackHandlerRegistry.registerTypeHandler("worldgen/biome", () -> biomeList);
        DatapackHandlerRegistry.registerTypeHandler("dimension_type", () -> dimensionTypeList);
        DatapackHandlerRegistry.registerTypeHandler("worldgen/noise_settings", () -> noiseSettingsList);
        DatapackHandlerRegistry.registerTypeHandler("worldgen/placed_feature", () -> placedFeatureList);
        DatapackHandlerRegistry.registerTypeHandler("worldgen/configured_feature", () -> configuredFeatureList);
        DatapackHandlerRegistry.registerTypeHandler("worldgen/configured_carver", ConfiguredCarverList::new);
        DatapackHandlerRegistry.registerTypeHandler("worldgen/noise", DatapackNoiseList::new);
        DatapackHandlerRegistry.registerTypeHandler("worldgen/density_function", DensityFunctionList::new);

        // Create temp world dir, symlink pack in, load, then clean up.
        Path tempDir = Files.createTempDirectory("databack-dto-test");
        try {
            Path datapacksDir = Files.createDirectories(tempDir.resolve("datapacks"));
            Files.createSymbolicLink(datapacksDir.resolve("minecraft"), VANILLA_PACK.toPath().toAbsolutePath());
            DatapackLoader.load(tempDir.toFile(), new TestDatapackWorldInfo());
        } finally {
            // Remove only the symlink and the datapacks dir — not the vanilla pack.
            File symlink = tempDir.resolve("datapacks/minecraft").toFile();
            symlink.delete();
            tempDir.resolve("datapacks").toFile().delete();
            tempDir.toFile().delete();
        }
    }

    // ---- Biome ---------------------------------------------------------------

    @Test
    void biome_plains_temperature() {
        DatapackBiome plains = biomeList.getBiome("minecraft:plains");
        assertNotNull(plains, "minecraft:plains not loaded");
        assertEquals(0.8f, plains.temperature, 0.001f);
    }

    @Test
    void biome_plains_downfall() {
        DatapackBiome plains = biomeList.getBiome("minecraft:plains");
        assertNotNull(plains);
        assertEquals(0.4f, plains.downfall, 0.001f);
    }

    @Test
    void biome_plains_hasPrecipitation() {
        DatapackBiome plains = biomeList.getBiome("minecraft:plains");
        assertNotNull(plains);
        assertTrue(plains.has_precipitation);
    }

    @Test
    void biome_plains_effectsPresent() {
        DatapackBiome plains = biomeList.getBiome("minecraft:plains");
        assertNotNull(plains);
        assertNotNull(plains.effects, "effects must not be null");
        assertNotNull(plains.effects.water_color, "water_color must not be null");
    }

    @Test
    void biome_ocean_temperature() {
        DatapackBiome ocean = biomeList.getBiome("minecraft:ocean");
        assertNotNull(ocean, "minecraft:ocean not loaded");
        assertEquals(0.5f, ocean.temperature, 0.001f);
    }

    // ---- DimensionType -------------------------------------------------------

    @Test
    void dimensionType_overworld_hasSkylight() {
        DimensionType overworld = dimensionTypeList.getDimensionType("minecraft:overworld");
        assertNotNull(overworld, "minecraft:overworld not loaded");
        assertTrue(overworld.has_skylight);
    }

    @Test
    void dimensionType_overworld_hasNoCeiling() {
        DimensionType overworld = dimensionTypeList.getDimensionType("minecraft:overworld");
        assertNotNull(overworld);
        assertFalse(overworld.has_ceiling);
    }

    @Test
    void dimensionType_overworld_coordinateScale() {
        DimensionType overworld = dimensionTypeList.getDimensionType("minecraft:overworld");
        assertNotNull(overworld);
        assertEquals(1.0, overworld.coordinate_scale, 0.001);
    }

    @Test
    void dimensionType_overworld_yBounds() {
        DimensionType overworld = dimensionTypeList.getDimensionType("minecraft:overworld");
        assertNotNull(overworld);
        assertEquals(-64, overworld.min_y);
        assertEquals(384, overworld.height);
        assertEquals(384, overworld.logical_height);
    }

    @Test
    void dimensionType_nether_hasCeiling() {
        DimensionType nether = dimensionTypeList.getDimensionType("minecraft:the_nether");
        assertNotNull(nether, "minecraft:the_nether not loaded");
        assertTrue(nether.has_ceiling);
    }

    @Test
    void dimensionType_nether_coordinateScale() {
        DimensionType nether = dimensionTypeList.getDimensionType("minecraft:the_nether");
        assertNotNull(nether);
        assertEquals(8.0, nether.coordinate_scale, 0.001);
    }

    // ---- NoiseGeneratorSettings ----------------------------------------------

    @Test
    void noiseSettings_overworld_seaLevel() {
        NoiseGeneratorSettings overworld = noiseSettingsList.getNoiseSettings("minecraft:overworld");
        assertNotNull(overworld, "minecraft:overworld noise settings not loaded");
        assertEquals(63, overworld.sea_level);
    }

    @Test
    void noiseSettings_overworld_defaultBlock() {
        NoiseGeneratorSettings overworld = noiseSettingsList.getNoiseSettings("minecraft:overworld");
        assertNotNull(overworld);
        assertNotNull(overworld.default_block, "default_block must not be null");
    }

    @Test
    void noiseSettings_overworld_noiseBounds() {
        NoiseGeneratorSettings overworld = noiseSettingsList.getNoiseSettings("minecraft:overworld");
        assertNotNull(overworld);
        assertNotNull(overworld.noise);
        assertEquals(-64, overworld.noise.min_y);
        assertEquals(384, overworld.noise.height);
    }

    @Test
    void noiseSettings_overworld_noiseRouterPresent() {
        NoiseGeneratorSettings overworld = noiseSettingsList.getNoiseSettings("minecraft:overworld");
        assertNotNull(overworld);
        assertNotNull(overworld.noise_router, "noise_router must not be null");
        assertNotNull(overworld.noise_router.barrier, "noise_router.barrier must not be null");
        assertNotNull(overworld.noise_router.final_density, "noise_router.final_density must not be null");
    }

    // ---- PlacedFeature -------------------------------------------------------

    @Test
    void placedFeature_oreCoalUpper_isLoaded() {
        PlacedFeature pf = placedFeatureList.getPlacedFeature("minecraft:ore_coal_upper");
        assertNotNull(pf, "minecraft:ore_coal_upper not loaded");
    }

    @Test
    void placedFeature_oreCoalUpper_featureReference() {
        PlacedFeature pf = placedFeatureList.getPlacedFeature("minecraft:ore_coal_upper");
        assertNotNull(pf);
        assertNotNull(pf.feature, "PlacedFeature.feature must not be null");
    }

    @Test
    void placedFeature_oreCoalUpper_placementModifiers() {
        PlacedFeature pf = placedFeatureList.getPlacedFeature("minecraft:ore_coal_upper");
        assertNotNull(pf);
        assertNotNull(pf.placement, "placement must not be null");
        assertEquals(4, pf.placement.length, "ore_coal_upper should have 4 placement modifiers");
    }

    // ---- ConfiguredFeature ---------------------------------------------------

    @Test
    void configuredFeature_oreCoal_isLoaded() {
        IConfiguredFeature cf = configuredFeatureList.getConfiguredFeature("minecraft:ore_coal");
        assertNotNull(cf, "minecraft:ore_coal not loaded");
    }

    @Test
    void configuredFeature_oak_isLoaded() {
        IConfiguredFeature cf = configuredFeatureList.getConfiguredFeature("minecraft:oak");
        assertNotNull(cf, "minecraft:oak not loaded");
    }

    // ---- Support types -------------------------------------------------------

    static class TestDatapackWorldInfo implements DatapackWorldInfo {

        @Override
        public List<String> getDatapackOrder() {
            return new ArrayList<>();
        }

        @Override
        public Set<String> getDisabledPacks() {
            return new HashSet<>();
        }

        @Override
        public void enable(String pack) {}

        @Override
        public void disable(String pack) {}

        @Override
        public void syncPackDeltas(List<Datapack> packs) {}

        @Override
        public List<Datapack> order(List<Datapack> packs) {
            return new ArrayList<>(packs);
        }
    }
}
