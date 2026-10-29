package databack.common.worldgen.carver.standin;

import org.jetbrains.annotations.ApiStatus;

/// Stand-in for the fluid constants the carvers name.
@ApiStatus.Internal
public final class CvFluids {

    public static final CvFluid WATER = new CvFluid("minecraft:water");
    public static final CvFluid LAVA = new CvFluid("minecraft:lava");

    private CvFluids() {}
}
