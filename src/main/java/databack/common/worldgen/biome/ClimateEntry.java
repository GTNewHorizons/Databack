package databack.common.worldgen.biome;

import java.util.Arrays;

import org.jetbrains.annotations.NotNull;

/// One entry of a multi-noise biome parameter list: a box in climate space and the biome it selects. All values are
/// quantized (value * 10000, see docs/worldgen/biome-source.md).
public final class ClimateEntry {

    public static final int TEMPERATURE = 0;
    public static final int HUMIDITY = 1;
    public static final int CONTINENTALNESS = 2;
    public static final int EROSION = 3;
    public static final int DEPTH = 4;
    public static final int WEIRDNESS = 5;
    public static final int PARAMETER_COUNT = 6;

    @NotNull
    private final String biome;
    private final long[] min;
    private final long[] max;
    private final long offset;

    /// Requires `min` and `max` to hold [#PARAMETER_COUNT] values, indexed by the constants above. Copies both.
    public ClimateEntry(@NotNull String biome, long @NotNull [] min, long @NotNull [] max, long offset) {
        if (min.length != PARAMETER_COUNT || max.length != PARAMETER_COUNT) {
            throw new IllegalArgumentException("Expected " + PARAMETER_COUNT + " parameters");
        }

        this.biome = biome;
        this.min = min.clone();
        this.max = max.clone();
        this.offset = offset;
    }

    /// The biome's resource id, e.g. `minecraft:plains`.
    @NotNull
    public String biome() {
        return biome;
    }

    public long min(int parameter) {
        return min[parameter];
    }

    public long max(int parameter) {
        return max[parameter];
    }

    public long offset() {
        return offset;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ClimateEntry other)) return false;
        return offset == other.offset && biome.equals(other.biome) && Arrays.equals(min, other.min)
            && Arrays.equals(max, other.max);
    }

    @Override
    public int hashCode() {
        return ((biome.hashCode() * 31 + Arrays.hashCode(min)) * 31 + Arrays.hashCode(max)) * 31 + Long.hashCode(offset);
    }

    @Override
    public String toString() {
        return biome + " min=" + Arrays.toString(min) + " max=" + Arrays.toString(max) + " offset=" + offset;
    }
}
