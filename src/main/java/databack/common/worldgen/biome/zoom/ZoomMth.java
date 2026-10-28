package databack.common.worldgen.biome.zoom;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;

/// Stand-in for vanilla's math helpers in rewritten biome zoom code. Behaviour established black-box (see
/// `ZoomStandInsTest`).
@ApiStatus.Internal
public final class ZoomMth {

    private ZoomMth() {}

    @Contract(pure = true)
    public static double square(double value) {
        return value * value;
    }

    /// Rounds towards negative infinity; out-of-range values saturate and NaN becomes 0, as `(int) Math.floor` does.
    @Contract(pure = true)
    public static int floor(double value) {
        return (int) Math.floor(value);
    }
}
