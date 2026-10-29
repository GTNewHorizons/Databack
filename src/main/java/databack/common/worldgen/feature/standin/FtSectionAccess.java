package databack.common.worldgen.feature.standin;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import databack.common.worldgen.vanilla.standin.VnBlockPos;
import databack.common.worldgen.vanilla.standin.VnMath;

/// Stand-in for direct access to the 16³ sections of a level, without block updates.
@ApiStatus.Internal
public final class FtSectionAccess implements AutoCloseable {

    @NotNull
    private final FtLevel level;

    public FtSectionAccess(@NotNull FtLevel level) {
        this.level = level;
    }

    /// The section holding `pos`, or null outside the build height.
    @Nullable
    public FtSection getSection(@NotNull VnBlockPos pos) {
        if (level.isOutsideBuildHeight(pos.getY())) return null;
        return new FtSection(level, VnMath.blockToSectionCoord(pos.getX()), VnMath.blockToSectionCoord(pos.getY()),
            VnMath.blockToSectionCoord(pos.getZ()));
    }

    @NotNull
    public FtBlockState getBlockState(@NotNull VnBlockPos pos) {
        return level.getBlockState(pos);
    }

    @Override
    public void close() {}
}
