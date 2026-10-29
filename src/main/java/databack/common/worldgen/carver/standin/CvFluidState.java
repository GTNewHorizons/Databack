package databack.common.worldgen.carver.standin;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/// Stand-in for a fluid state: a fluid's, or [#EMPTY].
@ApiStatus.Internal
public final class CvFluidState {

    public static final CvFluidState EMPTY = new CvFluidState(null);

    /// The block of the fluid; null for [#EMPTY].
    @Nullable
    private final CvBlock block;

    CvFluidState(@Nullable CvBlock block) {
        this.block = block;
    }

    public boolean isEmpty() {
        return block == null;
    }

    /// The fluid's block; air for [#EMPTY].
    @NotNull
    public CvBlockState createLegacyBlock() {
        return block == null ? CvBlocks.AIR.defaultBlockState() : block.defaultBlockState();
    }
}
