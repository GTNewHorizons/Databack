package databack.common.worldgen.aquifer.standin;

import org.jetbrains.annotations.ApiStatus;

/// Stand-in for the aquifer's global fluid picker.
@ApiStatus.Internal
@FunctionalInterface
public interface AqFluidPicker {

    AqFluidStatus computeFluid(int x, int y, int z);
}
