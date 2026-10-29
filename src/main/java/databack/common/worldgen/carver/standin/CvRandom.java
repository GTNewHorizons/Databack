package databack.common.worldgen.carver.standin;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import databack.common.worldgen.rng.RandomSource;
import databack.common.worldgen.rng.StandardRandom;

/// Stand-in for the random source the carvers draw from; wraps one of ours.
@ApiStatus.Internal
public final class CvRandom {

    @NotNull
    private final RandomSource source;

    public CvRandom(@NotNull RandomSource source) {
        this.source = source;
    }

    /// A seeded source of its own: the legacy random (`carver/probes/random.tsv`: identical to `create(seed)`).
    @NotNull
    public static CvRandom createThreadLocalInstance(long seed) {
        return new CvRandom(new StandardRandom(seed));
    }

    @NotNull
    public RandomSource source() {
        return source;
    }

    public float nextFloat() {
        return source.nextFloat();
    }

    public int nextInt(int bound) {
        return source.nextInt(bound);
    }

    public long nextLong() {
        return source.nextLong();
    }
}
