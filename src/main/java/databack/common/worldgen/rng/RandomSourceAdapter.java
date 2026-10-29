package databack.common.worldgen.rng;

import java.util.Random;

import org.jetbrains.annotations.NotNull;

/// A [RandomSource] seen as a [Random], for the datapack providers (float, height, int providers), which take one.
/// Every draw goes to the source, so values and draw counts are the source's.
public final class RandomSourceAdapter extends Random {

    private static final long serialVersionUID = 1L;

    /// Null only while [Random]'s constructor runs (it calls [#setSeed]).
    private final RandomSource source;

    public RandomSourceAdapter(@NotNull RandomSource source) {
        super(0);
        this.source = source;
    }

    @NotNull
    public RandomSource source() {
        return source;
    }

    @Override
    public synchronized void setSeed(long seed) {
        if (source != null) source.setSeed(seed);
    }

    @Override
    protected int next(int bits) {
        return source.nextInt() >>> (32 - bits);
    }

    @Override
    public int nextInt() {
        return source.nextInt();
    }

    @Override
    public int nextInt(int bound) {
        return source.nextInt(bound);
    }

    @Override
    public long nextLong() {
        return source.nextLong();
    }

    @Override
    public boolean nextBoolean() {
        return source.nextBoolean();
    }

    @Override
    public float nextFloat() {
        return source.nextFloat();
    }

    @Override
    public double nextDouble() {
        return source.nextDouble();
    }

    @Override
    public synchronized double nextGaussian() {
        return source.nextGaussian();
    }
}
