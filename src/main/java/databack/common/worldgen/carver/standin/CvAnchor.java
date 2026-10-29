package databack.common.worldgen.carver.standin;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import databack.common.dto.worldgen.height_provider.VerticalAnchor;

/// Stand-in for a vertical anchor: a datapack one, over the context's height range.
@ApiStatus.Internal
public final class CvAnchor {

    @NotNull
    private final VerticalAnchor anchor;

    public CvAnchor(@NotNull VerticalAnchor anchor) {
        this.anchor = anchor;
    }

    public int resolveY(@NotNull CvContext context) {
        return anchor.resolve(context.getMinGenY(), context.getGenDepth());
    }
}
