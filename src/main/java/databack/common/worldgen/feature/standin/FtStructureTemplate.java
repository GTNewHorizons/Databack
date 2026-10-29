package databack.common.worldgen.feature.standin;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/// Stand-in for the structure template helpers trees use.
@ApiStatus.Internal
public final class FtStructureTemplate {

    private FtStructureTemplate() {}

    /// No-op: modern reshapes the blocks around a placed shape; 1.7 blocks take their shape from their neighbours.
    public static void updateShapeAtEdge(@NotNull FtLevel level, int flags, @NotNull FtVoxelBits shape, int x, int y,
        int z) {}
}
