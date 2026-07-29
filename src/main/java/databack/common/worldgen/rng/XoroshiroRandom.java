package databack.common.worldgen.rng;

import databack.common.worldgen.rng.RNGUtils.Seed128bit;

@SuppressWarnings("unused")
public final class XoroshiroRandom implements RandomSource {

    private long x0, x1;

    public XoroshiroRandom(long x0, long x1) {
        this.x0 = x0;
        this.x1 = x1;
        if ((x0 | x1) == 0) {
            this.x0 = RNGUtils.GOLDEN_RATIO_64;
            this.x1 = RNGUtils.SILVER_RATIO_64;
        }
    }

    public XoroshiroRandom(long seed) {
        this.setSeed(RNGUtils.upgradeSeedTo128bit(seed));
    }

    public XoroshiroRandom(Seed128bit seed) {
        this(seed.seedLo(), seed.seedHi());
    }

    @Override
    public XoroshiroRandom fork() {
        return new XoroshiroRandom(nextLong(), nextLong());
    }

    @Override
    public XoroshiroRandomFactory forkFactory() {
        return new XoroshiroRandomFactory(nextLong(), nextLong());
    }

    @Override
    public long nextLong() {
        final long s0 = x0;
        long s1 = x1;
        final long result = Long.rotateLeft(s0 + s1, 17) + s0;

        s1 ^= s0;
        x0 = Long.rotateLeft(s0, 49) ^ s1 ^ (s1 << 21);
        x1 = Long.rotateLeft(s1, 28);

        return result;
    }

    public void setSeed(Seed128bit seed) {
        this.x0 = seed.seedLo();
        this.x1 = seed.seedHi();
    }

    @Override
    public void setSeed(long seed) {
        this.setSeed(RNGUtils.upgradeSeedTo128bit(seed));
    }

    @Override
    public int nextInt() {
        return (int)this.nextLong();
    }

    @Override
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("Bound must be positive");
        }
        long randomBits = Integer.toUnsignedLong(this.nextInt());
        long multipliedRandomBits = randomBits * (long)bound;
        long fractionalPart = multipliedRandomBits & 0xFFFFFFFFL;
        if (fractionalPart < (long)bound) {
            int unbiasedBucketsStartIndex = Integer.remainderUnsigned(~bound + 1, bound);
            while (fractionalPart < (long)unbiasedBucketsStartIndex) {
                randomBits = Integer.toUnsignedLong(this.nextInt());
                multipliedRandomBits = randomBits * (long)bound;
                fractionalPart = multipliedRandomBits & 0xFFFFFFFFL;
            }
        }
        long integerPart = multipliedRandomBits >> 32;
        return (int)integerPart;
    }

    @Override
    public boolean nextBoolean() {
        return (this.nextLong() & 1L) != 0L;
    }

    @Override
    public float nextFloat() {
        return (float)this.nextBits(24) * 5.9604645E-8f;
    }

    @Override
    public double nextDouble() {
        return (double)this.nextBits(53) * (double)1.110223E-16f;
    }

    @Override
    public double nextGaussian() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void consumeCount(int rounds) {
        for (int i = 0; i < rounds; ++i) {
            this.nextLong();
        }
    }

    private long nextBits(int bits) {
        return this.nextLong() >>> 64 - bits;
    }

    public double jumpDistance() {
        return 0x1.0p64;
    }

    public double leapDistance() {
        return 0x1.0p96;
    }

    private static final long[] JUMP_TABLE = { 0x2bd7a6a6e99c2ddcL, 0x0992ccaf6a6fca05L };

    private static final long[] LEAP_TABLE = { 0x360fd5f2cf8d5d99L, 0x9c6e6877736c46e3L };

    public void jump() {
        jumpAlgorithm(JUMP_TABLE);
    }

    public void leap() {
        jumpAlgorithm(LEAP_TABLE);
    }

    private void jumpAlgorithm(long[] table) {
        long s0 = 0, s1 = 0;
        for (int i = 0; i < table.length; i++) {
            for (int b = 0; b < 64; b++) {
                if ((table[i] & (1L << b)) != 0) {
                    s0 ^= x0;
                    s1 ^= x1;
                }
                nextLong();
            }
        }
        x0 = s0;
        x1 = s1;
    }
}
