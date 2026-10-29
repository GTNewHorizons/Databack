package databack.common.worldgen.carver.standin;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/// Stand-in for a canyon's shape configuration.
@ApiStatus.Internal
public final class CvCanyonShape {

    @NotNull
    public final CvFloatProvider distanceFactor;
    @NotNull
    public final CvFloatProvider thickness;
    public final int widthSmoothness;
    @NotNull
    public final CvFloatProvider horizontalRadiusFactor;
    public final float verticalRadiusDefaultFactor;
    public final float verticalRadiusCenterFactor;

    public CvCanyonShape(
        @NotNull CvFloatProvider distanceFactor, @NotNull CvFloatProvider thickness, int widthSmoothness,
        @NotNull CvFloatProvider horizontalRadiusFactor, float verticalRadiusDefaultFactor,
        float verticalRadiusCenterFactor
    ) {
        this.distanceFactor = distanceFactor;
        this.thickness = thickness;
        this.widthSmoothness = widthSmoothness;
        this.horizontalRadiusFactor = horizontalRadiusFactor;
        this.verticalRadiusDefaultFactor = verticalRadiusDefaultFactor;
        this.verticalRadiusCenterFactor = verticalRadiusCenterFactor;
    }
}
