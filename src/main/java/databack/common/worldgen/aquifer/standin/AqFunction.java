package databack.common.worldgen.aquifer.standin;

import org.jetbrains.annotations.ApiStatus;

/// Stand-in for a density function in rewritten aquifer code.
@ApiStatus.Internal
@FunctionalInterface
public interface AqFunction {

    double compute(AqContext ctx);
}
