package databack.common.dto.worldgen.height_provider;

import databack.common.WorldUtils;
import org.jetbrains.annotations.Nullable;

public class VerticalAnchor {

    @Nullable public Integer absolute;
    @Nullable public Integer above_bottom;
    @Nullable public Integer below_top;

    public int resolve() {
        if (absolute != null) return absolute;
        if (above_bottom != null) return above_bottom;
        if (below_top != null) return WorldUtils.getWorldHeight() - below_top;
        throw new IllegalStateException("Empty VerticalAnchor");
    }

}
