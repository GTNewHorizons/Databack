package databack.common.worldgen.feature.standin;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/// Stand-in for a fluid state: its fluid's id, as fluid sets and tags list it. Still and flowing fluids are not told
/// apart; features only see source blocks.
@ApiStatus.Internal
public final class FtFluidState {

    public static final FtFluidState EMPTY = new FtFluidState(null);
    public static final FtFluidState WATER = new FtFluidState("minecraft:water");
    public static final FtFluidState LAVA = new FtFluidState("minecraft:lava");

    @Nullable
    private final String fluid;

    private FtFluidState(@Nullable String fluid) {
        this.fluid = fluid;
    }

    public boolean is(@NotNull FtHolderSet fluids) {
        return fluid != null && fluids.contains(fluid);
    }

    /// Fluid tags are only water's and lava's, each holding its fluid.
    public boolean is(@NotNull FtTag tag) {
        return fluid != null && fluid.equals(tag.id);
    }

    /// Whether this is a source of `fluid`: features only see sources.
    public boolean isSourceOfType(@NotNull FtFluid fluid) {
        return fluid.id.equals(this.fluid);
    }

    public boolean isEmpty() {
        return fluid == null;
    }

    @Override
    public String toString() {
        return fluid == null ? "empty" : fluid;
    }
}
