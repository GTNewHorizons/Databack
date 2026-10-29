package databack.common.worldgen.carver.standin;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/// Stand-in for a carver's configuration: the fields every carver reads, by vanilla's names.
@ApiStatus.Internal
public class CvCarverConfiguration {

    public final float probability;
    @NotNull
    public final CvHeightProvider y;
    @NotNull
    public final CvFloatProvider yScale;
    @NotNull
    public final CvAnchor lavaLevel;
    @NotNull
    public final CvDebug debugSettings = CvDebug.DEFAULT;
    @NotNull
    public final CvBlockSet replaceable;

    public CvCarverConfiguration(
        float probability, @NotNull CvHeightProvider y, @NotNull CvFloatProvider yScale, @NotNull CvAnchor lavaLevel,
        @NotNull CvBlockSet replaceable
    ) {
        this.probability = probability;
        this.y = y;
        this.yScale = yScale;
        this.lavaLevel = lavaLevel;
        this.replaceable = replaceable;
    }
}
