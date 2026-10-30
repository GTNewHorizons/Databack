package databack.common.worldgen.feature.standin;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/// Stand-in for the generation context height providers sample in: the generator's layers, the dimension's whole
/// range. Unlike vanilla, not narrowed to the level's: a 1.7 level only holds part of the dimension (y = 0..255 of
/// an overworld spanning -64..319), and anchors such as `above_bottom` must still count from the dimension's bottom.
/// Positions outside the level are refused when written.
@ApiStatus.Internal
public class FtGenerationContext {

    private final int minGenY, genDepth;

    public FtGenerationContext(@NotNull FtChunkGenerator generator, @NotNull FtLevel level) {
        this.minGenY = generator.getMinY();
        this.genDepth = generator.getGenDepth();
    }

    public int getMinGenY() {
        return minGenY;
    }

    public int getGenDepth() {
        return genDepth;
    }
}
