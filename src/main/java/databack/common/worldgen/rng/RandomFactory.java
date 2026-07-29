package databack.common.worldgen.rng;

public interface RandomFactory {

    RandomSource newInstance();

    RandomSource fromHashOf(String text);

    RandomSource fromSeed(long seed);

    RandomSource at(int x, int y, int z);

}
