package databack.common.worldgen.feature.standin;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/// Stand-in for the generation context height providers sample in: the layers both the generator and the level
/// have.
@ApiStatus.Internal
public class FtGenerationContext {

    private final int minGenY, genDepth;

    public FtGenerationContext(@NotNull FtChunkGenerator generator, @NotNull FtLevel level) {
        this.minGenY = Math.max(generator.getMinY(), level.getMinY());
        int top = Math.min(generator.getMinY() + generator.getGenDepth(), level.getMinY() + level.getHeight());
        this.genDepth = top - minGenY;
    }

    public int getMinGenY() {
        return minGenY;
    }

    public int getGenDepth() {
        return genDepth;
    }
}
