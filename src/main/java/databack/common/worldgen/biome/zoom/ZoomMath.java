package databack.common.worldgen.biome.zoom;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;

/// Java 8 replacement for `Math` methods newer than Java 8 in rewritten biome zoom code. Each follows the JDK's
/// specification of the method it replaces.
@ApiStatus.Internal
public final class ZoomMath {

    private ZoomMath() {}

    /// `Math.floorMod(long, int)` (Java 9): the floor modulus, which has the sign of `divisor` and always fits an
    /// `int`. For a positive power of two that is the value's low bits, without a division.
    @Contract(pure = true)
    public static int floorMod(long value, int divisor) {
        if (divisor > 0 && (divisor & divisor - 1) == 0) return (int) (value & divisor - 1);
        return (int) Math.floorMod(value, (long) divisor);
    }
}
