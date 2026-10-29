package databack.common.worldgen.feature.standin;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import databack.common.worldgen.vanilla.standin.VnBlockPos;

/// Stand-in for the two-block-tall plants ([FtBlock#of] gives them this class).
@ApiStatus.Internal
public final class FtDoublePlantBlock extends FtBlock {

    private static final FtProperty HALF = FtProperty.of("half");

    FtDoublePlantBlock(@NotNull String id) {
        super(id);
    }

    /// Places the lower half of `state` at `pos` and the upper half above it.
    public static void placeAt(@NotNull FtLevel level, @NotNull FtBlockState state, @NotNull VnBlockPos pos, int flags) {
        level.setBlock(pos, (FtBlockState) state.setValue(HALF, "lower"), flags);
        level.setBlock(pos.above(), (FtBlockState) state.setValue(HALF, "upper"), flags);
    }
}
