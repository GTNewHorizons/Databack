package databack.test;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import databack.common.dto.dimension.BuiltinDimensionGenerators;
import databack.common.dto.dimension.BuiltinDimensionGenerators.BiomeEntry;
import databack.common.dto.dimension.BuiltinDimensionGenerators.MultiNoiseBiomes;
import databack.common.dto.dimension.BuiltinDimensionGenerators.NoiseDimensionGenerator;
import databack.common.dto.dimension.BuiltinDimensionGenerators.NoiseSettingsIdRef;
import databack.common.dto.dimension.Dimension;
import databack.common.dto.worldgen.biome.BuiltinBiomeAttributes;
import databack.common.dto.worldgen.biome.BuiltinBiomes;
import databack.common.dto.worldgen.block_predicate.BuiltinBlockPredicates;
import databack.common.dto.worldgen.block_state_provider.BuiltinBlockStateProviders;
import databack.common.dto.worldgen.carver.BuiltinCarvers;
import databack.common.dto.worldgen.configured_feature.BuiltinConfiguredFeatures;
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
import databack.common.dto.particle.BuiltinParticles;
import databack.common.serde.DatapackSerialization;
import databack.common.serde.MiscAdapters;

/**
 * Parses a real dimension JSON from the JJThunder datapack to verify the dimension DTO.
 *
 * <p>Skipped automatically when the pack directory is absent.
 */
class DimensionDtoTest {

    static final File JJTHUNDER_OVERWORLD = new File(
        "JJThunder_To_The_Max_1.21.0_1.21.1_v0.6.0/data/minecraft/dimension/overworld.json");

    static Dimension overworld;

    @BeforeAll
    static void setup() throws IOException {
        org.junit.jupiter.api.Assumptions.assumeTrue(
            JJTHUNDER_OVERWORLD.exists(),
            "JJThunder pack absent; skipping dimension DTO tests");

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

        String json = new String(Files.readAllBytes(JJTHUNDER_OVERWORLD.toPath()), StandardCharsets.UTF_8);
        overworld = DatapackSerialization.getGson().fromJson(json, Dimension.class);
    }

    @Test
    void dimension_overworldType() {
        assertNotNull(overworld, "overworld dimension not parsed");
        assertEquals("minecraft:overworld", overworld.type);
    }

    @Test
    void dimension_generatorIsNoise() {
        assertNotNull(overworld);
        assertInstanceOf(NoiseDimensionGenerator.class, overworld.generator);
    }

    @Test
    void dimension_noiseSettingsIsStringRef() {
        NoiseDimensionGenerator gen = (NoiseDimensionGenerator) overworld.generator;
        assertInstanceOf(NoiseSettingsIdRef.class, gen.settings);
        assertEquals("minecraft:overworld", ((NoiseSettingsIdRef) gen.settings).id);
    }

    @Test
    void dimension_biomeSourceIsMultiNoise() {
        NoiseDimensionGenerator gen = (NoiseDimensionGenerator) overworld.generator;
        assertInstanceOf(MultiNoiseBiomes.class, gen.biome_source);
    }

    @Test
    void dimension_multiNoiseBiomesHasEntries() {
        NoiseDimensionGenerator gen = (NoiseDimensionGenerator) overworld.generator;
        MultiNoiseBiomes biomes = (MultiNoiseBiomes) gen.biome_source;

        assertNull(biomes.preset, "inline biomes should not have a preset");
        assertNotNull(biomes.biomes, "biomes list must not be null");
        assertFalse(biomes.biomes.isEmpty(), "biomes list must not be empty");
    }

    @Test
    void dimension_biomeEntryHasRequiredFields() {
        NoiseDimensionGenerator gen = (NoiseDimensionGenerator) overworld.generator;
        MultiNoiseBiomes biomes = (MultiNoiseBiomes) gen.biome_source;

        BiomeEntry plains = biomes.biomes.stream()
            .filter(b -> "minecraft:plains".equals(b.biome))
            .findFirst()
            .orElse(null);

        assertNotNull(plains, "plains biome entry must be present");
        assertNotNull(plains.parameters, "plains parameters must not be null");
        assertNotNull(plains.parameters.temperature, "temperature must not be null");
        assertNotNull(plains.parameters.humidity, "humidity must not be null");
        assertNotNull(plains.parameters.continentalness, "continentalness must not be null");
        assertNotNull(plains.parameters.erosion, "erosion must not be null");
        assertNotNull(plains.parameters.weirdness, "weirdness must not be null");
        assertNotNull(plains.parameters.depth, "depth must not be null");
    }

    @Test
    void dimension_plainsTemperatureRange() {
        NoiseDimensionGenerator gen = (NoiseDimensionGenerator) overworld.generator;
        MultiNoiseBiomes biomes = (MultiNoiseBiomes) gen.biome_source;

        BiomeEntry plains = biomes.biomes.stream()
            .filter(b -> "minecraft:plains".equals(b.biome))
            .findFirst()
            .orElseThrow(() -> new AssertionError("plains biome not found for parameter check"));

        assertEquals(-0.2f, plains.parameters.temperature.min, 0.001f);
        assertEquals(0.2f, plains.parameters.temperature.max, 0.001f);
    }
}
