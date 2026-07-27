package databack.common.dto.worldgen.density_function;

import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

@FunctionalInterface
public interface IDensityFunction {

    default boolean hasTrait(DensityFuncTrait trait) {
        return false;
    }

    /// Calculates the values for this density function within the given cube (aka [ExtendedBlockStorage]).
    /// Only the voxels whose bit is set in the mask are calculated. The value for unmasked voxels is undefined.
    /// The returned buffer must be consumed and discarded immediately - it is only valid until
    /// [#compute(int, int, int, DensityMask)] is called on this density function again.
    /// @see DensityBuffer#discard()
    DensityBuffer compute(int cubeX, int cubeY, int cubeZ, DensityMask mask);
}
