package databack.common.worldgen.aquifer.standin;

import org.jetbrains.annotations.ApiStatus;

/// Stand-in for a random source.
@ApiStatus.Internal
@FunctionalInterface
public interface AqRandom {

    int nextInt(int bound);
}
