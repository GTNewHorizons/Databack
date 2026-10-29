package databack.common.dto.worldgen.height_provider;

import org.jetbrains.annotations.Nullable;

public class VerticalAnchor {

    @Nullable public Integer absolute;
    @Nullable public Integer above_bottom;
    @Nullable public Integer below_top;

    /// The height in a dimension spanning `height` blocks from `minY`: `above_bottom` counts from `minY`, `below_top`
    /// down from the top block (`minY + height - 1`).
    public int resolve(int minY, int height) {
        if (absolute != null) return absolute;
        if (above_bottom != null) return minY + above_bottom;
        if (below_top != null) return minY + height - 1 - below_top;
        throw new IllegalStateException("Empty VerticalAnchor");
    }

}
