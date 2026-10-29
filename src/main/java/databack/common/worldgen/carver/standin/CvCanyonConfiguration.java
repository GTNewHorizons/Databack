package databack.common.worldgen.carver.standin;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/// Stand-in for a canyon carver's configuration.
@ApiStatus.Internal
public final class CvCanyonConfiguration extends CvCarverConfiguration {

    public static final CvRegistration CODEC = CvRegistration.CARVER;

    @NotNull
    public final CvFloatProvider verticalRotation;
    @NotNull
    public final CvCanyonShape shape;

    public CvCanyonConfiguration(
        @NotNull CvCarverConfiguration base, @NotNull CvFloatProvider verticalRotation, @NotNull CvCanyonShape shape
    ) {
        super(base.probability, base.y, base.yScale, base.lavaLevel, base.replaceable);
        this.verticalRotation = verticalRotation;
        this.shape = shape;
    }
}
