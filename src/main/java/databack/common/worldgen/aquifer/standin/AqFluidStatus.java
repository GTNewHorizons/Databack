package databack.common.worldgen.aquifer.standin;

import java.util.Objects;

import org.jetbrains.annotations.ApiStatus;

/// Stand-in for the aquifer's fluid status: fluid strictly below a level, air at and above it (observed black-box).
/// Replaces vanilla's record, whose generated methods Java 8 cannot run.
@ApiStatus.Internal
public final class AqFluidStatus {

    /// Public: vanilla code reads these fields directly.
    public final int fluidLevel;
    public final AqBlockState fluidType;

    public AqFluidStatus(int fluidLevel, AqBlockState fluidType) {
        this.fluidLevel = fluidLevel;
        this.fluidType = fluidType;
    }

    public AqBlockState at(int y) {
        return y < fluidLevel ? fluidType : AqBlocks.AIR.defaultBlockState();
    }

    public int fluidLevel() {
        return fluidLevel;
    }

    public AqBlockState fluidType() {
        return fluidType;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof AqFluidStatus other && fluidLevel == other.fluidLevel && fluidType == other.fluidType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(fluidLevel, fluidType);
    }
}
