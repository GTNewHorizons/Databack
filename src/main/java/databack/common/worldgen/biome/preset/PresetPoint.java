package databack.common.worldgen.biome.preset;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import databack.common.worldgen.biome.ClimateEntry;

/// Stand-in for vanilla's climate parameter point (six ranges and a quantized offset) in extracted preset code.
@ApiStatus.Internal
public final class PresetPoint {

    @NotNull
    private final PresetParameter[] parameters;
    private final long offset;

    /// Parameter order matches vanilla's constructor; indices match [ClimateEntry]'s constants.
    public PresetPoint(
        @NotNull PresetParameter temperature, @NotNull PresetParameter humidity,
        @NotNull PresetParameter continentalness, @NotNull PresetParameter erosion, @NotNull PresetParameter depth,
        @NotNull PresetParameter weirdness, long offset
    ) {
        this.parameters = new PresetParameter[] { temperature, humidity, continentalness, erosion, depth, weirdness };
        this.offset = offset;
    }

    @NotNull
    public ClimateEntry toEntry(@NotNull String biome) {
        long[] min = new long[ClimateEntry.PARAMETER_COUNT];
        long[] max = new long[ClimateEntry.PARAMETER_COUNT];

        for (int i = 0; i < ClimateEntry.PARAMETER_COUNT; i++) {
            min[i] = parameters[i].min();
            max[i] = parameters[i].max();
        }

        return new ClimateEntry(biome, min, max, offset);
    }
}
