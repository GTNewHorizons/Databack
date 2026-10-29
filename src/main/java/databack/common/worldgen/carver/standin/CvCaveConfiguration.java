package databack.common.worldgen.carver.standin;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/// Stand-in for a cave carver's configuration.
@ApiStatus.Internal
public final class CvCaveConfiguration extends CvCarverConfiguration {

    public static final CvRegistration CODEC = CvRegistration.CARVER;

    @NotNull
    public final CvFloatProvider horizontalRadiusMultiplier;
    @NotNull
    public final CvFloatProvider verticalRadiusMultiplier;
    @NotNull
    public final CvFloatProvider floorLevel;

    public CvCaveConfiguration(
        @NotNull CvCarverConfiguration base, @NotNull CvFloatProvider horizontalRadiusMultiplier,
        @NotNull CvFloatProvider verticalRadiusMultiplier, @NotNull CvFloatProvider floorLevel
    ) {
        super(base.probability, base.y, base.yScale, base.lavaLevel, base.replaceable);
        this.horizontalRadiusMultiplier = horizontalRadiusMultiplier;
        this.verticalRadiusMultiplier = verticalRadiusMultiplier;
        this.floorLevel = floorLevel;
    }
}
