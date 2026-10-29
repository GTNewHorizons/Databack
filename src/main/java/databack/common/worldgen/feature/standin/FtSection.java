package databack.common.worldgen.feature.standin;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import databack.common.worldgen.vanilla.standin.VnBlockPos;
import databack.common.worldgen.vanilla.standin.VnMath;

/// Stand-in for a 16³ section of a level, addressed by coordinates within it.
@ApiStatus.Internal
public final class FtSection {

    /// What section writes are: no neighbour updates.
    private static final int FLAGS = 2;

    @NotNull
    private final FtLevel level;
    private final int minX, minY, minZ;

    FtSection(@NotNull FtLevel level, int sectionX, int sectionY, int sectionZ) {
        this.level = level;
        this.minX = VnMath.sectionToBlockCoord(sectionX);
        this.minY = VnMath.sectionToBlockCoord(sectionY);
        this.minZ = VnMath.sectionToBlockCoord(sectionZ);
    }

    @NotNull
    public FtBlockState getBlockState(int x, int y, int z) {
        return level.getBlockState(new VnBlockPos(minX + x, minY + y, minZ + z));
    }

    /// Sets the state and gives the one it replaced. `locked` (vanilla's thread check) is ignored.
    @NotNull
    public FtBlockState setBlockState(int x, int y, int z, @NotNull FtBlockState state, boolean locked) {
        VnBlockPos pos = new VnBlockPos(minX + x, minY + y, minZ + z);
        FtBlockState old = level.getBlockState(pos);
        level.setBlock(pos, state, FLAGS);
        return old;
    }
}
