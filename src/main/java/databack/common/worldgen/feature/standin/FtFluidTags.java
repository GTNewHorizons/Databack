package databack.common.worldgen.feature.standin;

import org.jetbrains.annotations.ApiStatus;

/// Stand-in for the fluid tag constants the feature classes name ([FtFluidState#is(FtTag)]).
@ApiStatus.Internal
public final class FtFluidTags {

    public static final FtTag WATER = new FtTag("minecraft:water");
    public static final FtTag LAVA = new FtTag("minecraft:lava");

    private FtFluidTags() {}
}
