package databack.common.worldgen.biome;

import java.util.Arrays;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/// One entry of a multi-noise biome parameter list: a box in climate space and the biome it selects. All values are
/// quantized ([#quantize]; docs/worldgen/biome-source.md section 1).
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

    /// Quantizes a climate value: truncates towards zero after multiplying in `float` (so `-0.78f` becomes `-7799`).
    @Contract(pure = true)
    public static long quantize(float value) {
        return (long) (value * 10000.0F);
    }

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

    /// The squared distance from this entry to `target` (spec section 4): per parameter, the squared distance to the
    /// range (0 inside it), plus the squared offset. Computed in `long`; see [#axisDistance] for its range.
    @Contract(pure = true)
    public long distance(long @NotNull [] target) {
        long sum = offset * offset;
        for (int i = 0; i < PARAMETER_COUNT; i++) {
            long d = axisDistance(target[i], min[i], max[i]);
            sum += d * d;
        }
        return sum;
    }

    /// How far `value` lies outside `[lo, hi]` (0 inside).
    @Contract(pure = true)
    public static long axisDistance(long value, long lo, long hi) {
        if (value < lo) return lo - value;
        if (value > hi) return value - hi;
        return 0;
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
        int hash = biome.hashCode() * 31 + Arrays.hashCode(min);
        return (hash * 31 + Arrays.hashCode(max)) * 31 + Long.hashCode(offset);
    }

    @Override
    public String toString() {
        return biome + " min=" + Arrays.toString(min) + " max=" + Arrays.toString(max) + " offset=" + offset;
    }
}
