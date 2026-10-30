package databack.common.worldgen.feature.standin;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/// Stand-in for a fluid state: its fluid, and whether it is a source. As in modern, flowing fluids are fluids of
/// their own (`minecraft:flowing_water`) in fluid sets, while fluid tags hold both forms.
@ApiStatus.Internal
public final class FtFluidState {

    public static final FtFluidState EMPTY = new FtFluidState(null, true);
    public static final FtFluidState WATER = new FtFluidState("minecraft:water", true);
    public static final FtFluidState FLOWING_WATER = new FtFluidState("minecraft:water", false);
    public static final FtFluidState LAVA = new FtFluidState("minecraft:lava", true);
    public static final FtFluidState FLOWING_LAVA = new FtFluidState("minecraft:lava", false);

    /// The source form's id.
    @Nullable
    private final String fluid;
    private final boolean source;

    private FtFluidState(@Nullable String fluid, boolean source) {
        this.fluid = fluid;
        this.source = source;
    }

    /// The fluid's id as fluid sets list it.
    @Nullable
    private String id() {
        if (fluid == null || source) return fluid;
        return fluid.replace(":", ":flowing_");
    }

    public boolean is(@NotNull FtHolderSet fluids) {
        String id = id();
        return id != null && fluids.contains(id);
    }

    /// Fluid tags are only water's and lava's, each holding both forms of its fluid.
    public boolean is(@NotNull FtTag tag) {
        return fluid != null && fluid.equals(tag.id);
    }

    public boolean isSource() {
        return fluid != null && source;
    }

    public boolean isSourceOfType(@NotNull FtFluid fluid) {
        return source && fluid.id.equals(this.fluid);
    }

    public boolean isEmpty() {
        return fluid == null;
    }

    @Override
    public String toString() {
        String id = id();
        return id == null ? "empty" : id;
    }
}
