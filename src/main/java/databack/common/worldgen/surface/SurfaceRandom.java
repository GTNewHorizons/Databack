package databack.common.worldgen.surface;

import org.jetbrains.annotations.Contract;

/// Databack's own positional randomness for the parts of surface rules where vanilla's seeded values cannot be
/// observed (docs/worldgen/surface-rules.md, "Approximation"). Deterministic in the seed, a name and a position.
public final class SurfaceRandom {

    private SurfaceRandom() {}

    /// A value in `[0, 1)` for `(seed, name, x, y, z)`.
    @Contract(pure = true)
    public static double at(long seed, String name, int x, int y, int z) {
        long h = mix(seed ^ mix(name.hashCode()));
        h = mix(h ^ x);
        h = mix(h ^ y);
        h = mix(h ^ z);
        return (h >>> 11) * 0x1.0p-53;
    }

    /// SplitMix64's finaliser.
    private static long mix(long v) {
        v += 0x9E3779B97F4A7C15L;
        v = (v ^ (v >>> 30)) * 0xBF58476D1CE4E5B9L;
        v = (v ^ (v >>> 27)) * 0x94D049BB133111EBL;
        return v ^ (v >>> 31);
    }
}
