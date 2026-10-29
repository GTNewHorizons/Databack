package databack.common.worldgen.carver.standin;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/// Stand-in for a block the carvers name ([CvBlocks]), or the block of any other state ([CvStatePool]).
@ApiStatus.Internal
public final class CvBlock {

    @NotNull
    public final String id;
    @Nullable
    private final CvFluid fluid;
    @NotNull
    private final CvBlockState defaultState;

    CvBlock(@NotNull String id, @Nullable CvFluid fluid) {
        this.id = id;
        this.fluid = fluid;
        this.defaultState = new CvBlockState(this, null);
    }

    @NotNull
    public CvBlockState defaultBlockState() {
        return defaultState;
    }

    /// The fluid this block holds, or null.
    @Nullable
    CvFluid fluid() {
        return fluid;
    }

    @Override
    public String toString() {
        return id;
    }
}
