package databack.common.worldgen.aquifer.standin;

import org.jetbrains.annotations.ApiStatus;

/// Stand-in for a positional random factory.
@ApiStatus.Internal
@FunctionalInterface
public interface AqRandomFactory {

    AqRandom at(int x, int y, int z);
}
