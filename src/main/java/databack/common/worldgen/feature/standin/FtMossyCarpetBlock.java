package databack.common.worldgen.feature.standin;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import databack.common.worldgen.rng.RandomSource;
import databack.common.worldgen.vanilla.standin.VnBlockPos;

/// Stand-in for the mossy carpets ([FtBlock#of] gives them this class).
@ApiStatus.Internal
public final class FtMossyCarpetBlock extends FtBlock {

    FtMossyCarpetBlock(@NotNull String id) {
        super(id);
    }

    /// Places a pale moss carpet at `pos`. Vanilla also hangs random side carpets from it; those are not placed, and
    /// their draws are not made.
    public static void placeAt(@NotNull FtLevel level, @NotNull VnBlockPos pos, @NotNull RandomSource random, int flags) {
        level.setBlock(pos, FtBlock.of("minecraft:pale_moss_carpet").defaultBlockState(), flags);
    }
}
