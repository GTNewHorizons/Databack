package databack.common.worldgen.rng;

import com.gtnewhorizon.gtnhlib.util.StdLCG;

public class StandardRandom implements BitRandomSource {

    private static final int MODULUS_BITS = 48;
    private static final long MODULUS_MASK = 0xFFFFFFFFFFFFL;
    private static final long MULTIPLIER = 0x5DEECE66DL;
    private static final long INCREMENT = 11L;

    private long seed;

    public StandardRandom(long seed) {
        setSeed(seed);
    }

    @Override
    public StandardRandom fork() {
        return new StandardRandom(this.nextLong());
    }

    @Override
    public StandardRandomFactory forkFactory() {
        return new StandardRandomFactory(this.nextLong());
    }

    @Override
    public int nextBits(int bits) {
        this.seed = this.seed * MULTIPLIER + INCREMENT & MODULUS_MASK;
        return (int)(this.seed >> MODULUS_BITS - bits);
    }

    @Override
    public void setSeed(long seed) {
        this.seed = (seed ^ MULTIPLIER) & MODULUS_MASK;
    }

    @Override
    public double nextGaussian() {
        throw new UnsupportedOperationException();
    }
}
