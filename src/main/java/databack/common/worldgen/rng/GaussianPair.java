package databack.common.worldgen.rng;

import org.jetbrains.annotations.NotNull;

/// Normally distributed draws by the polar method, as `java.util.Random` documents it: each pair of uniform draws
/// gives two values, the second kept for the next call (`feature/probes/gaussian.tsv`). `Math.log`, not the
/// documented `StrictMath.log`: vanilla's draws differ from the latter in the last bit. One per random; not
/// thread-safe.
final class GaussianPair {

    private double kept;
    private boolean hasKept;

    double next(@NotNull RandomSource random) {
        if (hasKept) {
            hasKept = false;
            return kept;
        }

        double a, b, radius;
        do {
            a = 2 * random.nextDouble() - 1;
            b = 2 * random.nextDouble() - 1;
            radius = a * a + b * b;
        } while (radius >= 1 || radius == 0);

        double scale = Math.sqrt(-2 * Math.log(radius) / radius);
        kept = b * scale;
        hasKept = true;
        return a * scale;
    }

    /// Drops the kept value: reseeding starts a new sequence.
    void reset() {
        hasKept = false;
    }
}
