package databack.common.worldgen.aquifer.standin;

import org.jetbrains.annotations.ApiStatus;

/// Stand-in for a density function's position in rewritten aquifer code.
@ApiStatus.Internal
public interface AqContext {

    int blockX();

    int blockY();

    int blockZ();
}
