package databack.common.worldgen.rng;

public interface BitRandomSource extends RandomSource {
    float FLOAT_MULTIPLIER = 5.9604645E-8f;
    double DOUBLE_MULTIPLIER = 1.110223E-16f;

    int nextBits(int bits);

    @Override
    default int nextInt() {
        return this.nextBits(32);
    }

    @Override
    default int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("Bound must be positive");
        }
        if ((bound & bound - 1) == 0) {
            return (int)((long)bound * (long)this.nextBits(31) >> 31);
        }

        int modulo;
        int sample;
        while ((sample = this.nextBits(31)) - (modulo = sample % bound) + bound - 1 < 0) { }

        return modulo;
    }

    @Override
    default long nextLong() {
        int upper = this.nextBits(32);
        int lower = this.nextBits(32);
        long shifted = (long)upper << 32;
        return shifted + (long)lower;
    }

    @Override
    default boolean nextBoolean() {
        return this.nextBits(1) != 0;
    }

    @Override
    default float nextFloat() {
        return (float)this.nextBits(24) * FLOAT_MULTIPLIER;
    }

    @Override
    default double nextDouble() {
        int upper = this.nextBits(26);
        int lower = this.nextBits(27);
        long combined = ((long)upper << 27) + (long)lower;
        return (double)combined * DOUBLE_MULTIPLIER;
    }
}

