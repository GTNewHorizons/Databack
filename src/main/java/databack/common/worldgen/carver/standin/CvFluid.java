package databack.common.worldgen.carver.standin;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/// Stand-in for a fluid ([CvFluids]); each has one state.
@ApiStatus.Internal
public final class CvFluid {

    @NotNull
    private final CvFluidState state;

    CvFluid(@NotNull String blockId) {
        this.state = new CvFluidState(new CvBlock(blockId, this));
    }

    @NotNull
    public CvFluidState defaultFluidState() {
        return state;
    }
}
