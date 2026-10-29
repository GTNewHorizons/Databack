package databack.common.worldgen.rng;

import org.jetbrains.annotations.NotNull;

/// A [BitRandomSource] whose bits come from another source's bit draws (the top bits of a xoroshiro long), so wider
/// draws (longs, doubles) are built from several of them; seeding reseeds the other source. The worldgen random
/// vanilla seeds per chunk behaves this way over either source (`carver/probes/large_feature_seed.tsv`).
public final class SourceBits implements BitRandomSource {

    @NotNull
    private final RandomSource source;

    public SourceBits(@NotNull RandomSource source) {
        this.source = source;
    }

    @Override
    public int nextBits(int bits) {
        if (source instanceof XoroshiroRandom) return (int) ((XoroshiroRandom) source).nextBits(bits);
        if (source instanceof BitRandomSource) return ((BitRandomSource) source).nextBits(bits);
        return source.nextInt() >>> (32 - bits);
    }

    @Override
    public void setSeed(long seed) {
        source.setSeed(seed);
    }

    @Override
    public RandomSource fork() {
        return source.fork();
    }

    @Override
    public RandomFactory forkFactory() {
        return source.forkFactory();
    }

    @Override
    public double nextGaussian() {
        return source.nextGaussian();
    }
}
