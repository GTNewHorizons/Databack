package databack.common.worldgen.rng;

public class StandardRandomFactory implements RandomFactory {

    private final long seed;

    public StandardRandomFactory(long seed) {
        this.seed = seed;
    }

    @Override
    public StandardRandom newInstance() {
        return new StandardRandom(seed);
    }

    @Override
    public StandardRandom fromHashOf(String text) {
        int positionalSeed = text.hashCode();
        return new StandardRandom((long)positionalSeed ^ this.seed);
    }

    @Override
    public StandardRandom fromSeed(long seed) {
        return new StandardRandom(seed);
    }

    @Override
    public StandardRandom at(int x, int y, int z) {
        long positionalSeed = RNGUtils.getPosSeed(x, y, z);
        long randomSeed = positionalSeed ^ this.seed;
        return new StandardRandom(randomSeed);
    }
}
