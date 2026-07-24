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

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import databack.common.dto.worldgen.biome.BuiltinBiomeAttributes;
import databack.common.dto.worldgen.biome.BuiltinBiomes;
import databack.common.dto.worldgen.biome.DatapackBiome;
import databack.common.dto.worldgen.block_predicate.BuiltinBlockPredicates;
import databack.common.dto.worldgen.block_state_provider.BuiltinBlockStateProviders;
import databack.common.dto.worldgen.carver.BuiltinCarvers;
import databack.common.dto.worldgen.configured_feature.BuiltinConfiguredFeatures;
import databack.common.dto.worldgen.configured_feature.IConfiguredFeature;
import databack.common.dto.worldgen.density_function.BuiltinDensityFunctions;
import databack.common.dto.worldgen.float_provider.BuiltinFloatProviders;
import databack.common.dto.worldgen.height_provider.BuiltinHeightProviders;
import databack.common.dto.worldgen.int_provider.BuiltinIntProviders;
import databack.common.dto.worldgen.noise_settings.NoiseGeneratorSettings;
import databack.common.dto.worldgen.placed_feature.BuiltinPlacementModifiers;
import databack.common.dto.worldgen.processor_list.BuiltinProcessors;
import databack.common.dto.worldgen.structure.BuiltinStructures;
import databack.common.dto.worldgen.structure_set.BuiltinStructurePlacements;
import databack.common.dto.worldgen.template_pool.BuiltinPoolElements;
import databack.common.dto.worldgen.placed_feature.PlacedFeature;
import databack.common.dto.particle.BuiltinParticles;
import databack.common.dto.dimension.BuiltinDimensionGenerators;
import databack.common.dto.dimension.BuiltinDimensionGenerators.MultiNoiseBiomes;
import databack.common.dto.dimension.BuiltinDimensionGenerators.NoiseDimensionGenerator;
import databack.common.dto.dimension.BuiltinDimensionGenerators.NoiseSettingsIdRef;
import databack.common.dto.dimension_type.DimensionType;
import databack.common.dto.worldgen.multi_noise_biome_source.MultiNoiseBiomeSourceParameterList;
import databack.common.dto.worldgen.processor_list.ProcessorList;
import databack.common.dto.worldgen.structure.IStructure;
import databack.common.dto.worldgen.structure_set.StructureSet;
import databack.common.dto.worldgen.template_pool.TemplatePool;
import databack.common.dto.worldgen.world_preset.WorldPreset;
import databack.common.handlers.BiomeList;
import databack.common.handlers.ConfiguredCarverList;
import databack.common.handlers.ConfiguredFeatureList;
import databack.common.handlers.DatapackHandlerRegistry;
import databack.common.handlers.DatapackNoiseList;
import databack.common.handlers.DensityFunctionList;
import databack.common.handlers.DimensionTypeList;
import databack.common.handlers.MultiNoiseBiomeSourceParameterListHandler;
import databack.common.handlers.NoiseSettingsList;
import databack.common.handlers.PlacedFeatureList;
import databack.common.handlers.ProcessorListHandler;
import databack.common.handlers.StructureList;
import databack.common.handlers.StructureSetList;
import databack.common.handlers.TemplatePoolList;
import databack.common.handlers.WorldPresetList;
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
    static MultiNoiseBiomeSourceParameterListHandler multiNoiseBiomeSourceParameterListHandler;
    static ProcessorListHandler processorListHandler;
    static TemplatePoolList templatePoolList;
    static StructureList structureList;
    static StructureSetList structureSetList;
    static WorldPresetList worldPresetList;

    @BeforeAll
    static void loadVanillaPack() throws IOException {
        assumeTrue(VANILLA_PACK.exists(), "Vanilla pack absent; copy it to misc/test-packs/minecraft");

        // Serde must be initialized before any handler is registered.
        DatapackSerialization.resetForTesting();
        DatapackSerialization.init();
        MiscAdapters.init();
        BuiltinCarvers.init();
        BuiltinBlockPredicates.init();
        BuiltinBlockStateProviders.init();
        BuiltinConfiguredFeatures.init();
        BuiltinDensityFunctions.init();
        BuiltinFloatProviders.init();
        BuiltinHeightProviders.init();
        BuiltinIntProviders.init();
        BuiltinParticles.init();
        BuiltinPlacementModifiers.init();
        BuiltinBiomeAttributes.init();
        BuiltinBiomes.init();
        BuiltinProcessors.init();
        BuiltinPoolElements.init();
        BuiltinStructures.init();
        BuiltinStructurePlacements.init();
        NoiseGeneratorSettings.init();
        BuiltinDimensionGenerators.init();
        DatapackSerialization.finish();

        DatapackHandlerRegistry.clearForTesting();

        biomeList = new BiomeList();
        dimensionTypeList = new DimensionTypeList();
        noiseSettingsList = new NoiseSettingsList();
        placedFeatureList = new PlacedFeatureList();
        configuredFeatureList = new ConfiguredFeatureList();
        multiNoiseBiomeSourceParameterListHandler = new MultiNoiseBiomeSourceParameterListHandler();
        processorListHandler = new ProcessorListHandler();
        templatePoolList = new TemplatePoolList();
        structureList = new StructureList();
        structureSetList = new StructureSetList();
        worldPresetList = new WorldPresetList();

        DatapackHandlerRegistry.registerTypeHandler("worldgen/biome", () -> biomeList);
        DatapackHandlerRegistry.registerTypeHandler("dimension_type", () -> dimensionTypeList);
        DatapackHandlerRegistry.registerTypeHandler("worldgen/noise_settings", () -> noiseSettingsList);
        DatapackHandlerRegistry.registerTypeHandler("worldgen/placed_feature", () -> placedFeatureList);
        DatapackHandlerRegistry.registerTypeHandler("worldgen/configured_feature", () -> configuredFeatureList);
        DatapackHandlerRegistry.registerTypeHandler("worldgen/configured_carver", ConfiguredCarverList::new);
        DatapackHandlerRegistry.registerTypeHandler("worldgen/noise", DatapackNoiseList::new);
        DatapackHandlerRegistry.registerTypeHandler("worldgen/density_function", DensityFunctionList::new);
        DatapackHandlerRegistry.registerTypeHandler("worldgen/multi_noise_biome_source_parameter_list",
                () -> multiNoiseBiomeSourceParameterListHandler);
        DatapackHandlerRegistry.registerTypeHandler("worldgen/processor_list", () -> processorListHandler);
        DatapackHandlerRegistry.registerTypeHandler("worldgen/template_pool", () -> templatePoolList);
        DatapackHandlerRegistry.registerTypeHandler("worldgen/structure", () -> structureList);
        DatapackHandlerRegistry.registerTypeHandler("worldgen/structure_set", () -> structureSetList);
        DatapackHandlerRegistry.registerTypeHandler("worldgen/world_preset", () -> worldPresetList);

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

    @Test
    void configuredFeature_lushCavesClay_isLoaded() {
        // random_boolean_selector — exercises feature_false / feature_true as IPlacedFeatureRef
        IConfiguredFeature cf = configuredFeatureList.getConfiguredFeature("minecraft:lush_caves_clay");
        assertNotNull(cf, "minecraft:lush_caves_clay not loaded");
    }

    @Test
    void configuredFeature_forestFlowers_isLoaded() {
        // simple_random_selector — exercises IPlacedFeatureRef[] features (inline PlacedFeature objects)
        IConfiguredFeature cf = configuredFeatureList.getConfiguredFeature("minecraft:forest_flowers");
        assertNotNull(cf, "minecraft:forest_flowers not loaded");
    }

    @Test
    void configuredFeature_mangroveVegetation_isLoaded() {
        // random_selector — exercises WeightedPlacedFeature features and IPlacedFeatureRef default
        IConfiguredFeature cf = configuredFeatureList.getConfiguredFeature("minecraft:mangrove_vegetation");
        assertNotNull(cf, "minecraft:mangrove_vegetation not loaded");
    }

    // ---- IPlacedFeatureRef (biome features) ----------------------------------

    @Test
    void biome_plains_featuresLoaded() {
        DatapackBiome plains = biomeList.getBiome("minecraft:plains");
        assertNotNull(plains);
        assertNotNull(plains.features, "features must not be null");
        assertTrue(plains.features.length > 0, "features must have at least one decoration step");
    }

    @Test
    void biome_plains_hasOreDirt() {
        DatapackBiome plains = biomeList.getBiome("minecraft:plains");
        assertNotNull(plains);
        assertTrue(plains.hasFeature("minecraft:ore_dirt"), "plains must contain minecraft:ore_dirt");
    }

    @Test
    void biome_plains_doesNotHaveNetherFeature() {
        DatapackBiome plains = biomeList.getBiome("minecraft:plains");
        assertNotNull(plains);
        assertFalse(plains.hasFeature("minecraft:basalt_blobs"), "plains must not contain nether feature");
    }

    // ---- ProcessorList -------------------------------------------------------

    @Test
    void processorList_empty_isLoaded() {
        ProcessorList empty = processorListHandler.getProcessorList("minecraft:empty");
        assertNotNull(empty, "minecraft:empty processor list not loaded");
        assertNotNull(empty.processors);
        assertEquals(0, empty.processors.size());
    }

    @Test
    void processorList_mossify10Percent_hasOneRule() {
        ProcessorList mossify = processorListHandler.getProcessorList("minecraft:mossify_10_percent");
        assertNotNull(mossify, "minecraft:mossify_10_percent not loaded");
        assertEquals(1, mossify.processors.size());
    }

    // ---- TemplatePool --------------------------------------------------------

    @Test
    void templatePool_empty_isLoaded() {
        TemplatePool empty = templatePoolList.getTemplatePool("minecraft:empty");
        assertNotNull(empty, "minecraft:empty template pool not loaded");
        assertNotNull(empty.elements);
        assertEquals(0, empty.elements.length);
    }

    @Test
    void templatePool_villagePlainsHouses_hasElements() {
        TemplatePool pool = templatePoolList.getTemplatePool("minecraft:village/plains/houses");
        assertNotNull(pool, "minecraft:village/plains/houses not loaded");
        assertTrue(pool.elements.length > 0, "village/plains/houses must have elements");
    }

    @Test
    void templatePool_villagePlainsHouses_fallback() {
        TemplatePool pool = templatePoolList.getTemplatePool("minecraft:village/plains/houses");
        assertNotNull(pool);
        assertEquals("minecraft:village/plains/terminators", pool.fallback);
    }

    // ---- Structure -----------------------------------------------------------

    @Test
    void structure_ancientCity_isLoaded() {
        IStructure s = structureList.getStructure("minecraft:ancient_city");
        assertNotNull(s, "minecraft:ancient_city not loaded");
    }

    @Test
    void structure_mineshaft_isLoaded() {
        IStructure s = structureList.getStructure("minecraft:mineshaft");
        assertNotNull(s, "minecraft:mineshaft not loaded");
    }

    @Test
    void structure_stronghold_isLoaded() {
        IStructure s = structureList.getStructure("minecraft:stronghold");
        assertNotNull(s, "minecraft:stronghold not loaded");
    }

    // ---- StructureSet --------------------------------------------------------

    @Test
    void structureSet_ancientCities_isLoaded() {
        StructureSet ss = structureSetList.getStructureSet("minecraft:ancient_cities");
        assertNotNull(ss, "minecraft:ancient_cities not loaded");
        assertNotNull(ss.structures);
        assertEquals(1, ss.structures.length);
        assertEquals("minecraft:ancient_city", ss.structures[0].structure);
    }

    @Test
    void structureSet_ancientCities_placement() {
        StructureSet ss = structureSetList.getStructureSet("minecraft:ancient_cities");
        assertNotNull(ss);
        assertNotNull(ss.placement, "placement must not be null");
    }

    @Test
    void structureSet_villages_hasMultipleStructures() {
        StructureSet ss = structureSetList.getStructureSet("minecraft:villages");
        assertNotNull(ss, "minecraft:villages not loaded");
        assertTrue(ss.structures.length > 1, "villages must have multiple structure variants");
    }

    @Test
    void structureSet_strongholds_concentricRings() {
        StructureSet ss = structureSetList.getStructureSet("minecraft:strongholds");
        assertNotNull(ss, "minecraft:strongholds not loaded");
        assertNotNull(ss.placement);
    }

    // ---- MultiNoiseBiomeSourceParameterList ----------------------------------

    @Test
    void multiNoise_overworld_isLoaded() {
        MultiNoiseBiomeSourceParameterList overworld =
                multiNoiseBiomeSourceParameterListHandler.getParameterList("minecraft:overworld");
        assertNotNull(overworld, "minecraft:overworld parameter list not loaded");
    }

    @Test
    void multiNoise_overworld_preset() {
        MultiNoiseBiomeSourceParameterList overworld =
                multiNoiseBiomeSourceParameterListHandler.getParameterList("minecraft:overworld");
        assertNotNull(overworld);
        assertEquals("minecraft:overworld", overworld.preset);
    }

    @Test
    void multiNoise_nether_isLoaded() {
        MultiNoiseBiomeSourceParameterList nether =
                multiNoiseBiomeSourceParameterListHandler.getParameterList("minecraft:nether");
        assertNotNull(nether, "minecraft:nether parameter list not loaded");
    }

    @Test
    void multiNoise_nether_preset() {
        MultiNoiseBiomeSourceParameterList nether =
                multiNoiseBiomeSourceParameterListHandler.getParameterList("minecraft:nether");
        assertNotNull(nether);
        assertEquals("minecraft:nether", nether.preset);
    }

    // ---- WorldPreset ---------------------------------------------------------

    @Test
    void worldPreset_normal_isLoaded() {
        WorldPreset normal = worldPresetList.getWorldPreset("minecraft:normal");
        assertNotNull(normal, "minecraft:normal world preset not loaded");
        assertNotNull(normal.dimensions);
    }

    @Test
    void worldPreset_normal_hasThreeDimensions() {
        WorldPreset normal = worldPresetList.getWorldPreset("minecraft:normal");
        assertNotNull(normal);
        assertTrue(normal.dimensions.containsKey("minecraft:overworld"), "normal must have overworld");
        assertTrue(normal.dimensions.containsKey("minecraft:the_nether"), "normal must have the_nether");
        assertTrue(normal.dimensions.containsKey("minecraft:the_end"), "normal must have the_end");
    }

    @Test
    void worldPreset_normal_overworldIsNoise() {
        WorldPreset normal = worldPresetList.getWorldPreset("minecraft:normal");
        assertNotNull(normal);
        assertInstanceOf(NoiseDimensionGenerator.class, normal.dimensions.get("minecraft:overworld").generator);
    }

    @Test
    void worldPreset_normal_overworldUsesMultiNoisePreset() {
        WorldPreset normal = worldPresetList.getWorldPreset("minecraft:normal");
        assertNotNull(normal);
        NoiseDimensionGenerator gen = (NoiseDimensionGenerator) normal.dimensions.get("minecraft:overworld").generator;
        assertInstanceOf(MultiNoiseBiomes.class, gen.biome_source);
        MultiNoiseBiomes biomes = (MultiNoiseBiomes) gen.biome_source;
        assertNotNull(biomes.preset, "normal overworld biome source should use preset");
        assertNull(biomes.biomes, "normal overworld biome source should not have inline biomes");
    }

    @Test
    void worldPreset_normal_overworldSettingsRef() {
        WorldPreset normal = worldPresetList.getWorldPreset("minecraft:normal");
        assertNotNull(normal);
        NoiseDimensionGenerator gen = (NoiseDimensionGenerator) normal.dimensions.get("minecraft:overworld").generator;
        assertInstanceOf(NoiseSettingsIdRef.class, gen.settings);
        assertEquals("minecraft:overworld", ((NoiseSettingsIdRef) gen.settings).id);
    }

    @Test
    void worldPreset_flat_overworldIsFlat() {
        WorldPreset flat = worldPresetList.getWorldPreset("minecraft:flat");
        assertNotNull(flat, "minecraft:flat world preset not loaded");
        assertInstanceOf(
            BuiltinDimensionGenerators.FlatDimensionGenerator.class,
            flat.dimensions.get("minecraft:overworld").generator);
    }

    @Test
    void worldPreset_flat_layersPresent() {
        WorldPreset flat = worldPresetList.getWorldPreset("minecraft:flat");
        assertNotNull(flat);
        BuiltinDimensionGenerators.FlatDimensionGenerator gen =
            (BuiltinDimensionGenerators.FlatDimensionGenerator) flat.dimensions.get("minecraft:overworld").generator;
        assertNotNull(gen.settings);
        assertNotNull(gen.settings.layers);
        assertFalse(gen.settings.layers.isEmpty(), "flat world must have at least one layer");
    }

    @Test
    void worldPreset_debug_overworldIsDebug() {
        WorldPreset debug = worldPresetList.getWorldPreset("minecraft:debug_all_block_states");
        assertNotNull(debug, "minecraft:debug_all_block_states world preset not loaded");
        assertInstanceOf(
            BuiltinDimensionGenerators.DebugDimensionGenerator.class,
            debug.dimensions.get("minecraft:overworld").generator);
    }

    // ---- Support types -------------------------------------------------------

    static class TestDatapackWorldInfo implements DatapackWorldInfo {

        @Override
        public net.minecraft.nbt.NBTTagCompound db$saveDatapackInfo() { return new net.minecraft.nbt.NBTTagCompound(); }

        @Override
        public void db$loadDatapackInfo(net.minecraft.nbt.NBTTagCompound tag) {}

        @Override
        public List<String> db$getDatapackOrder() {
            return new ArrayList<>();
        }

        @Override
        public Set<String> db$getDisabledPacks() {
            return new HashSet<>();
        }

        @Override
        public void db$enable(String pack) {}

        @Override
        public void db$disable(String pack) {}

        @Override
        public void db$syncPackDeltas(@NotNull List<Datapack> packs) {}

        @Override
        public @NotNull List<Datapack> db$order(@NotNull List<Datapack> packs) {
            return new ArrayList<>(packs);
        }
    }
}
