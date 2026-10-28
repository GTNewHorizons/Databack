package databack.common.worldgen.biome.preset;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/// Stand-in for vanilla's climate parameter range in extracted preset code. Its behaviour was established black-box
/// (see `PresetStandInsTest`). Values are quantized.
@ApiStatus.Internal
public final class PresetParameter {

    private final long min;
    private final long max;

    private PresetParameter(long min, long max) {
        this.min = min;
        this.max = max;
    }

    @NotNull
    @Contract("_ -> new")
    public static PresetParameter point(float value) {
        return span(value, value);
    }

    /// Throws when `min` quantizes above `max`.
    @NotNull
    @Contract("_, _ -> new")
    public static PresetParameter span(float min, float max) {
        return of(PresetClimate.quantizeCoord(min), PresetClimate.quantizeCoord(max));
    }

    /// The range from `from`'s minimum to `to`'s maximum (not the hull of both). Throws when that range is empty.
    @NotNull
    @Contract("_, _ -> new")
    public static PresetParameter span(@NotNull PresetParameter from, @NotNull PresetParameter to) {
        return of(from.min, to.max);
    }

    private static PresetParameter of(long min, long max) {
        if (min > max) throw new IllegalArgumentException("min > max: " + min + " > " + max);
        return new PresetParameter(min, max);
    }

    public long min() {
        return min;
    }

    public long max() {
        return max;
    }

    @Override
    public String toString() {
        return "[" + min + ", " + max + "]";
    }
}
