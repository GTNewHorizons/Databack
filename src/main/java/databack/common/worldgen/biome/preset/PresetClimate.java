package databack.common.worldgen.biome.preset;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import databack.common.worldgen.biome.ClimateEntry;

/// Stand-in for vanilla's climate helper methods in extracted preset code.
@ApiStatus.Internal
public final class PresetClimate {

    private PresetClimate() {}

    /// See [ClimateEntry#quantize].
    @Contract(pure = true)
    public static long quantizeCoord(float value) {
        return ClimateEntry.quantize(value);
    }

    @NotNull
    @Contract("_, _, _, _, _, _, _ -> new")
    public static PresetPoint parameters(
        @NotNull PresetParameter temperature, @NotNull PresetParameter humidity,
        @NotNull PresetParameter continentalness, @NotNull PresetParameter erosion, @NotNull PresetParameter depth,
        @NotNull PresetParameter weirdness, float offset
    ) {
        return new PresetPoint(temperature, humidity, continentalness, erosion, depth, weirdness,
            quantizeCoord(offset));
    }

    /// Each value becomes a single-point range.
    @NotNull
    @Contract("_, _, _, _, _, _, _ -> new")
    public static PresetPoint parameters(
        float temperature, float humidity, float continentalness, float erosion, float depth, float weirdness,
        float offset
    ) {
        return parameters(PresetParameter.point(temperature), PresetParameter.point(humidity),
            PresetParameter.point(continentalness), PresetParameter.point(erosion), PresetParameter.point(depth),
            PresetParameter.point(weirdness), offset);
    }
}
