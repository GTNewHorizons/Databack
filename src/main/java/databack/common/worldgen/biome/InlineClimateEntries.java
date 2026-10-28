package databack.common.worldgen.biome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import databack.common.dto.dimension.BuiltinDimensionGenerators.BiomeEntry;
import databack.common.dto.worldgen.noise_settings.NoiseGeneratorSettings.ClimateParameters;
import databack.common.dto.worldgen.noise_settings.NoiseGeneratorSettings.ClimatePoint;

/// Converts a multi-noise source's inline `biomes` list into [ClimateEntry]s (spec section 2).
public final class InlineClimateEntries {

    private static final Logger LOGGER = LogManager.getLogger("databack-biome-source");

    private InlineClimateEntries() {}

    /// The entries, in list order. Invalid entries (missing fields, or a range with `min > max`) are logged as errors
    /// naming `source` and dropped.
    @NotNull
    @Contract("_, _ -> new")
    public static List<ClimateEntry> convert(@NotNull List<BiomeEntry> biomes, @NotNull String source) {
        List<ClimateEntry> entries = new ArrayList<>(biomes.size());

        for (int i = 0; i < biomes.size(); i++) {
            ClimateEntry entry = convert(biomes.get(i));
            if (entry != null) {
                entries.add(entry);
            } else {
                LOGGER.error("Biome source {}: dropping invalid entry {} (missing field or a range with min > max)",
                    source, i);
            }
        }

        return Collections.unmodifiableList(entries);
    }

    @Nullable
    private static ClimateEntry convert(@Nullable BiomeEntry biome) {
        if (biome == null || biome.biome == null || biome.parameters == null) return null;

        ClimateParameters p = biome.parameters;
        ClimatePoint[] points = new ClimatePoint[ClimateEntry.PARAMETER_COUNT];
        points[ClimateEntry.TEMPERATURE] = p.temperature;
        points[ClimateEntry.HUMIDITY] = p.humidity;
        points[ClimateEntry.CONTINENTALNESS] = p.continentalness;
        points[ClimateEntry.EROSION] = p.erosion;
        points[ClimateEntry.DEPTH] = p.depth;
        points[ClimateEntry.WEIRDNESS] = p.weirdness;

        long[] min = new long[ClimateEntry.PARAMETER_COUNT];
        long[] max = new long[ClimateEntry.PARAMETER_COUNT];

        for (int i = 0; i < ClimateEntry.PARAMETER_COUNT; i++) {
            if (points[i] == null) return null;
            min[i] = ClimateEntry.quantize(points[i].min);
            max[i] = ClimateEntry.quantize(points[i].max);
            if (min[i] > max[i]) return null;
        }

        return new ClimateEntry(biome.biome, min, max, ClimateEntry.quantize(p.offset));
    }
}
