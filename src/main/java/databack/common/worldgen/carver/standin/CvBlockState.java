package databack.common.worldgen.carver.standin;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/// Stand-in for a block state. Instances are unique per state ([CvStatePool]), so the carvers may compare them with
/// `==`. 1.7 blocks have no waterlogging, so no state has properties.
@ApiStatus.Internal
public final class CvBlockState {

    @NotNull
    final CvBlock block;
    /// What the state stands for in the chunk ([CvStatePool]); null for a [CvBlocks] block's default state.
    @Nullable
    final Object payload;

    CvBlockState(@NotNull CvBlock block, @Nullable Object payload) {
        this.block = block;
        this.payload = payload;
    }

    /// Whether the state is of `block` (a [CvBlock]).
    public boolean is(Object block) {
        return this.block == block;
    }

    public boolean is(@NotNull CvBlockSet set) {
        return set.contains(this);
    }

    @NotNull
    public CvFluidState getFluidState() {
        CvFluid fluid = block.fluid();
        return fluid == null ? CvFluidState.EMPTY : fluid.defaultFluidState();
    }

    public boolean hasProperty(CvProperty property) {
        return false;
    }

    /// Unreachable: no state has properties ([#hasProperty]).
    public Object setValue(CvProperty property, Comparable<?> value) {
        return this;
    }

    @Override
    public String toString() {
        return payload == null ? block.id : block.id + "(" + payload + ")";
    }
}
