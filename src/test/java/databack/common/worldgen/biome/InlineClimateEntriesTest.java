package databack.common.worldgen.biome;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import databack.common.dto.dimension.BuiltinDimensionGenerators.BiomeEntry;
import databack.common.dto.dimension.BuiltinDimensionGenerators.MultiNoiseBiomes;
import databack.common.dto.dimension.BuiltinDimensionGenerators.MultiNoisePresetIdRef;
import databack.common.dto.dimension.BuiltinDimensionGenerators.MultiNoisePresetInlineRef;
import databack.common.dto.worldgen.multi_noise_biome_source.MultiNoiseBiomeSourceParameterList;
import databack.common.dto.worldgen.noise_settings.NoiseGeneratorSettings.ClimateParameters;
import databack.common.dto.worldgen.noise_settings.NoiseGeneratorSettings.ClimatePoint;

class InlineClimateEntriesTest {

    @Test
    void convertsInListOrderWithVanillaQuantization() {
        List<ClimateEntry> entries = InlineClimateEntries.convert(
            Arrays.asList(entry("minecraft:a", -0.78f, 0.25f), entry("minecraft:b", 0.5f, 0.5f)), "test");

        assertEquals(2, entries.size());
        assertEquals("minecraft:a", entries.get(0).biome());
        assertEquals(-7799, entries.get(0).min(ClimateEntry.EROSION));
        assertEquals(2500, entries.get(0).max(ClimateEntry.EROSION));
        assertEquals(1000, entries.get(0).min(ClimateEntry.DEPTH));
        assertEquals(6000, entries.get(0).min(ClimateEntry.WEIRDNESS));
        assertEquals(3750, entries.get(0).offset());
        assertEquals("minecraft:b", entries.get(1).biome());
    }

    @Test
    void invalidEntriesAreDropped() {
        BiomeEntry reversed = entry("minecraft:reversed", 0.5f, -0.5f);
        BiomeEntry missing = entry("minecraft:missing", 0, 0);
        missing.parameters.depth = null;

        List<ClimateEntry> entries = InlineClimateEntries.convert(
            Arrays.asList(reversed, missing, entry("minecraft:ok", 0, 0)), "test");

        assertEquals(1, entries.size());
        assertEquals("minecraft:ok", entries.get(0).biome());
    }

    @Test
    void resolvesInlineListsAndPresets() {
        List<ClimateEntry> preset = Collections.singletonList(
            new ClimateEntry("minecraft:p", new long[6], new long[6], 0));

        MultiNoiseBiomeSourceParameterList list = new MultiNoiseBiomeSourceParameterList();
        list.preset = "minecraft:overworld";

        MultiNoiseBiomes byId = new MultiNoiseBiomes();
        byId.preset = new MultiNoisePresetIdRef("minecraft:overworld");
        assertEquals(preset, resolve(byId, list, preset));

        MultiNoiseBiomes inlineRef = new MultiNoiseBiomes();
        inlineRef.preset = new MultiNoisePresetInlineRef(list);
        assertEquals(preset, resolve(inlineRef, null, preset));

        MultiNoiseBiomes unknownList = new MultiNoiseBiomes();
        unknownList.preset = new MultiNoisePresetIdRef("pack:missing");
        assertEquals(Collections.emptyList(), resolve(unknownList, null, preset));
        assertEquals(Collections.emptyList(), resolve(byId, list, null));
        assertEquals(Collections.emptyList(), resolve(new MultiNoiseBiomes(), list, preset));

        MultiNoiseBiomes inline = new MultiNoiseBiomes();
        inline.biomes = Collections.singletonList(entry("minecraft:inline", 0, 0));
        assertEquals("minecraft:inline", resolve(inline, list, preset).get(0).biome());
    }

    private static List<ClimateEntry> resolve(
        MultiNoiseBiomes source, MultiNoiseBiomeSourceParameterList list, List<ClimateEntry> preset
    ) {
        return MultiNoiseParameters.resolve(source, "test", id -> list, id -> preset);
    }

    /// Erosion spans `[erosionMin, erosionMax]`; the others are fixed points (depth 0.1, weirdness 0.6, offset 0.375).
    private static BiomeEntry entry(String biome, float erosionMin, float erosionMax) {
        ClimateParameters p = new ClimateParameters();
        p.temperature = new ClimatePoint(0, 0);
        p.humidity = new ClimatePoint(0, 0);
        p.continentalness = new ClimatePoint(0, 0);
        p.erosion = new ClimatePoint(erosionMin, erosionMax);
        p.depth = new ClimatePoint(0.1f, 0.1f);
        p.weirdness = new ClimatePoint(0.6f, 0.6f);
        p.offset = 0.375f;

        BiomeEntry e = new BiomeEntry();
        e.biome = biome;
        e.parameters = p;
        return e;
    }
}
