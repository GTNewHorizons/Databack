package databack.common.worldgen.feature.standin;

import org.jetbrains.annotations.ApiStatus;

/// Stand-in for the fluid constants the feature classes name.
@ApiStatus.Internal
public final class FtFluids {

    public static final FtFluid EMPTY = new FtFluid("minecraft:empty");
    public static final FtFluid WATER = new FtFluid("minecraft:water");
    public static final FtFluid LAVA = new FtFluid("minecraft:lava");
    public static final FtFluid FLOWING_WATER = new FtFluid("minecraft:flowing_water");
    public static final FtFluid FLOWING_LAVA = new FtFluid("minecraft:flowing_lava");

    private FtFluids() {}
}
