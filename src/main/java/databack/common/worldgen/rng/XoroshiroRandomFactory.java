package databack.common.worldgen.rng;

import databack.common.worldgen.rng.RNGUtils.Seed128bit;

public class XoroshiroRandomFactory implements RandomFactory {

    private final long x0, x1;

    public XoroshiroRandomFactory(long x0, long x1) {
        this.x0 = x0;
        this.x1 = x1;
    }

    public XoroshiroRandomFactory(long seed) {
        this(RNGUtils.mixStafford13(seed ^= RNGUtils.SILVER_RATIO_64), RNGUtils.mixStafford13(seed + RNGUtils.GOLDEN_RATIO_64));
    }

    @Override
    public XoroshiroRandom newInstance() {
        return new XoroshiroRandom(x0, x1);
    }

    @Override
    public XoroshiroRandom fromHashOf(String text) {
        Seed128bit seed = RNGUtils.seedFromHashOf(text);
        return new XoroshiroRandom(seed.xor(this.x0, this.x1));
    }

    @Override
    public XoroshiroRandom fromSeed(long seed) {
        return new XoroshiroRandom(seed ^ this.x0, seed ^ this.x1);
    }

    @Override
    public XoroshiroRandom at(int x, int y, int z) {
        long positionalSeed = RNGUtils.getPosSeed(x, y, z);
        long randomSeed = positionalSeed ^ this.x0;
        return new XoroshiroRandom(randomSeed, this.x1);
    }
}
