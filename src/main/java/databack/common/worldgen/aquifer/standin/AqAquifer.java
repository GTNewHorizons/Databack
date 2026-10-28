package databack.common.worldgen.aquifer.standin;

import org.jetbrains.annotations.ApiStatus;

/// Added to vanilla's aquifer when it is rewritten, so it can be called without reflection.
@ApiStatus.Internal
public interface AqAquifer {

    /// What fills a block of the given terrain density: a marker of [AqBlocks], or null for solid.
    AqBlockState computeSubstance(AqContext ctx, double density);

    boolean shouldScheduleFluidUpdate();
}
