package databack.common.worldgen.carver.standin;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import databack.common.worldgen.aquifer.standin.AqAquifer;
import databack.common.worldgen.aquifer.standin.AqBlockState;
import databack.common.worldgen.aquifer.standin.AqBlocks;
import databack.common.worldgen.aquifer.standin.AqContext;

/// Stand-in for the chunk's aquifer as the carvers see it: the aquifer's markers as carver block states.
@ApiStatus.Internal
public final class CvAquifer {

    @NotNull
    private final AqAquifer aquifer;

    public CvAquifer(@NotNull AqAquifer aquifer) {
        this.aquifer = aquifer;
    }

    /// What fills a carved block of the given density: air, water, lava, or null to keep it solid.
    @Nullable
    public CvBlockState computeSubstance(@NotNull AqContext ctx, double density) {
        AqBlockState state = aquifer.computeSubstance(ctx, density);
        if (state == null) return null;
        if (state.isAir()) return CvBlocks.AIR.defaultBlockState();
        return state.is(AqBlocks.LAVA) ? CvBlocks.LAVA.defaultBlockState() : CvBlocks.WATER.defaultBlockState();
    }

    public boolean shouldScheduleFluidUpdate() {
        return aquifer.shouldScheduleFluidUpdate();
    }
}
