package databack.common.worldgen.biome.zoom;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;

/// Stand-in for vanilla's quart position helpers in rewritten biome zoom code.
@ApiStatus.Internal
public final class ZoomQuartPos {

    private ZoomQuartPos() {}

    /// The quart cell containing block coordinate `block` (rounds towards negative infinity).
    @Contract(pure = true)
    public static int fromBlock(int block) {
        return block >> 2;
    }
}
